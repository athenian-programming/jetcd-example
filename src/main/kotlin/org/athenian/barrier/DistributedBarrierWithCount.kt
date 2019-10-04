/*
 *
 *  Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package org.athenian.barrier

import com.sudothought.common.concurrent.isFinished
import com.sudothought.common.time.Conversions.Static.timeUnitToDuration
import com.sudothought.common.util.randomId
import io.etcd.jetcd.Client
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.options.WatchOption
import io.etcd.jetcd.watch.WatchEvent.EventType.DELETE
import io.etcd.jetcd.watch.WatchEvent.EventType.PUT
import org.athenian.jetcd.appendToPath
import org.athenian.jetcd.asByteSequence
import org.athenian.jetcd.asPutOption
import org.athenian.jetcd.asString
import org.athenian.jetcd.countChildren
import org.athenian.jetcd.delete
import org.athenian.jetcd.deleteOp
import org.athenian.jetcd.ensureTrailing
import org.athenian.jetcd.equals
import org.athenian.jetcd.getChildrenKeys
import org.athenian.jetcd.getStringValue
import org.athenian.jetcd.keepAliveWith
import org.athenian.jetcd.keyIsPresent
import org.athenian.jetcd.putOp
import org.athenian.jetcd.transaction
import org.athenian.jetcd.watcher
import org.athenian.jetcd.withKvClient
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.days

/*
    First node creates subnode /ready
    Each node creates its own subnode with keepalive on it
    Each node creates a watch for DELETE on /ready and PUT on any waiter
    Query the number of children after each PUT on waiter and DELETE /ready if memberCount seen
    Leave if DELETE of /ready is seen
*/
class DistributedBarrierWithCount(val url: String,
                                  val barrierPath: String,
                                  val memberCount: Int,
                                  val clientId: String) : Closeable {

    constructor(url: String,
                barrierPath: String,
                memberCount: Int) : this(url, barrierPath, memberCount, "Client:${randomId(9)}")

    private val client = lazy { Client.builder().endpoints(url).build() }
    private val kvClient = lazy { client.value.kvClient }
    private val leaseClient = lazy { client.value.leaseClient }
    private val watchClient = lazy { client.value.watchClient }
    private val executor = lazy { Executors.newSingleThreadExecutor() }
    private val readyPath = barrierPath.appendToPath("ready")
    private val waitingPrefix = barrierPath.appendToPath("waiting")

    init {
        require(url.isNotEmpty()) { "URL cannot be empty" }
        require(barrierPath.isNotEmpty()) { "Barrier path cannot be empty" }
        require(memberCount > 0) { "Member count must be > 0" }
    }

    private val isReadySet: Boolean get() = kvClient.keyIsPresent(readyPath)

    val waiterCount: Long get() = kvClient.countChildren(waitingPrefix)

    @Throws(InterruptedException::class)
    fun waitOnBarrier(): Boolean = waitOnBarrier(Long.MAX_VALUE.days)

    @Throws(InterruptedException::class)
    fun waitOnBarrier(timeout: Long, timeUnit: TimeUnit): Boolean =
        waitOnBarrier(timeUnitToDuration(timeout, timeUnit))

    @Throws(InterruptedException::class)
    fun waitOnBarrier(timeout: Duration): Boolean {

        val uniqueToken = "$clientId:${randomId(9)}"

        // Do a CAS on the /ready name. If it is not found, then set it
        kvClient.transaction {
            If(equals(readyPath, CmpTarget.version(0)))
            Then(putOp(readyPath, uniqueToken))
        }

        val waitLatch = CountDownLatch(1)
        val waitingPath = waitingPrefix.appendToPath(uniqueToken)
        val lease = leaseClient.value.grant(2).get()

        val txn =
            kvClient.transaction {
                If(equals(waitingPath, CmpTarget.version(0)))
                Then(putOp(waitingPath, uniqueToken, lease.asPutOption))
            }

        check(txn.isSucceeded) { "Failed to set waitingPath" }
        check(kvClient.getStringValue(waitingPath) == uniqueToken) { "Failed to assign waitingPath unique value" }

        // Keep key alive
        executor.value.submit { leaseClient.value.keepAliveWith(lease) { waitLatch.await() } }

        fun checkWaiterCount() {
            // First see if /ready is missing
            if (!isReadySet) {
                waitLatch.countDown()
            } else {
                if (waiterCount >= memberCount) {

                    waitLatch.countDown()

                    // Delete /ready key
                    kvClient.transaction {
                        If(equals(readyPath, CmpTarget.version(0)))
                        Then()
                        Else(deleteOp(readyPath))
                    }
                }
            }
        }

        checkWaiterCount()

        // Do not bother starting watcher if latch is already done
        if (waitLatch.isFinished)
            return true

        // Watch for DELETE of /ready and PUTS on /waiters/*
        val adjustedKey = barrierPath.ensureTrailing("/")
        val watchOption = WatchOption.newBuilder().withPrefix(adjustedKey.asByteSequence).build()
        watchClient.watcher(adjustedKey, watchOption) { watchResponse ->
            watchResponse.events
                .forEach { watchEvent ->
                    val key = watchEvent.keyValue.key.asString
                    when {
                        key.startsWith(waitingPrefix) && watchEvent.eventType == PUT -> checkWaiterCount()
                        key.startsWith(readyPath) && watchEvent.eventType == DELETE -> waitLatch.countDown()
                    }
                }

        }.use {
            // Check one more time in case watch missed the delete just after last check
            checkWaiterCount()

            val success = waitLatch.await(timeout.toLongMilliseconds(), TimeUnit.MILLISECONDS)
            // Cleanup if a time-out occurred
            if (!success) {
                waitLatch.countDown() // Release keep-alive waiting on latch.
                kvClient.delete(waitingPath)  // This is redundant but waiting for keep-alive to stop is slower
            }

            return@waitOnBarrier success
        }
    }

    override fun close() {
        if (watchClient.isInitialized())
            watchClient.value.close()

        if (leaseClient.isInitialized())
            leaseClient.value.close()

        if (kvClient.isInitialized())
            kvClient.value.close()

        if (client.isInitialized())
            client.value.close()

        if (executor.isInitialized())
            executor.value.shutdown()
    }

    companion object {
        fun reset(url: String, barrierPath: String) {
            require(barrierPath.isNotEmpty()) { "Barrier path cannot be empty" }
            Client.builder().endpoints(url).build()
                .use { client ->
                    client.withKvClient { kvClient ->
                        // Delete all children
                        kvClient.getChildrenKeys(barrierPath).forEach { kvClient.delete(it) }
                    }
                }
        }
    }
}