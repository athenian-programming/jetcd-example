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

import com.sudothought.common.concurrent.withLock
import com.sudothought.common.delegate.AtomicDelegates.atomicBoolean
import com.sudothought.common.delegate.AtomicDelegates.nullableReference
import com.sudothought.common.time.Conversions.Static.timeUnitToDuration
import com.sudothought.common.util.randomId
import io.etcd.jetcd.Client
import io.etcd.jetcd.CloseableClient
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.watch.WatchEvent.EventType.DELETE
import org.athenian.common.EtcdRecipeRuntimeException
import org.athenian.jetcd.asPutOption
import org.athenian.jetcd.delete
import org.athenian.jetcd.equalTo
import org.athenian.jetcd.getStringValue
import org.athenian.jetcd.keepAlive
import org.athenian.jetcd.keyIsPresent
import org.athenian.jetcd.putOp
import org.athenian.jetcd.transaction
import org.athenian.jetcd.watcher
import org.athenian.jetcd.withKvClient
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.days

class DistributedBarrier(val url: String,
                         val barrierPath: String,
                         private val waitOnMissingBarriers: Boolean,
                         val clientId: String) : Closeable {

    constructor(url: String,
                barrierPath: String,
                waitOnMissingBarrier: Boolean = true) : this(url,
                                                             barrierPath,
                                                             waitOnMissingBarrier,
                                                             "Client:${randomId(9)}")

    private val semaphore = Semaphore(1, true)
    private val client = lazy { Client.builder().endpoints(url).build() }
    private val kvClient = lazy { client.value.kvClient }
    private val leaseClient = lazy { client.value.leaseClient }
    private val watchClient = lazy { client.value.watchClient }
    private var closeCalled by atomicBoolean(false)
    private var keepAliveLease by nullableReference<CloseableClient?>(null)
    private var barrierRemoved by atomicBoolean(false)

    init {
        require(url.isNotEmpty()) { "URL cannot be empty" }
        require(barrierPath.isNotEmpty()) { "Barrier path cannot be empty" }
    }

    val isBarrierSet: Boolean
        get() = semaphore.withLock {
            checkCloseNotCalled()
            kvClient.keyIsPresent(barrierPath)
        }

    fun setBarrier(): Boolean =
        semaphore.withLock {
            checkCloseNotCalled()
            if (kvClient.keyIsPresent(barrierPath))
                false
            else {
                // Create unique token to avoid collision from clients with same id
                val uniqueToken = "$clientId:${randomId(9)}"

                // Prime lease with 2 seconds to give keepAlive a chance to get started
                val lease = leaseClient.value.grant(2).get()

                // Do a CAS on the key name. If it is not found, then set it
                val txn =
                    kvClient.transaction {
                        If(equalTo(barrierPath, CmpTarget.version(0)))
                        Then(putOp(barrierPath, uniqueToken, lease.asPutOption))
                    }

                // Check to see if unique value was successfully set in the CAS step
                if (txn.isSucceeded && kvClient.getStringValue(barrierPath) == uniqueToken) {
                    keepAliveLease = leaseClient.value.keepAlive(lease)
                    true
                } else {
                    false
                }
            }
        }

    fun removeBarrier(): Boolean =
        semaphore.withLock {
            checkCloseNotCalled()
            if (barrierRemoved) {
                false
            } else {
                keepAliveLease?.close()
                keepAliveLease = null
                barrierRemoved = true
                true
            }
        }

    @Throws(InterruptedException::class)
    fun waitOnBarrier(): Boolean = waitOnBarrier(Long.MAX_VALUE.days)

    @Throws(InterruptedException::class)
    fun waitOnBarrier(timeout: Long, timeUnit: TimeUnit): Boolean =
        waitOnBarrier(timeUnitToDuration(timeout, timeUnit))

    @Throws(InterruptedException::class)
    fun waitOnBarrier(timeout: Duration): Boolean {

        checkCloseNotCalled()

        // Check if barrier is present before using watcher
        if (!waitOnMissingBarriers && !isBarrierSet)
            return true

        val waitLatch = CountDownLatch(1)

        watchClient.watcher(barrierPath) { watchResponse ->
            watchResponse.events
                .forEach { watchEvent ->
                    if (watchEvent.eventType == DELETE)
                        waitLatch.countDown()
                }

        }.use {
            // Check one more time in case watch missed the delete just after last check
            if (!waitOnMissingBarriers && !isBarrierSet)
                waitLatch.countDown()

            return@waitOnBarrier waitLatch.await(timeout.toLongMilliseconds(), TimeUnit.MILLISECONDS)
        }
    }

    private fun checkCloseNotCalled() {
        if (closeCalled) throw EtcdRecipeRuntimeException("close() already closed")
    }

    override fun close() {
        semaphore.withLock {
            if (!closeCalled) {
                keepAliveLease?.close()
                keepAliveLease = null

                if (watchClient.isInitialized())
                    watchClient.value.close()

                if (leaseClient.isInitialized())
                    leaseClient.value.close()

                if (kvClient.isInitialized())
                    kvClient.value.close()

                if (client.isInitialized())
                    client.value.close()

                closeCalled = true
            }
        }
    }

    companion object {
        fun reset(url: String, barrierPath: String) {
            require(barrierPath.isNotEmpty()) { "Barrier path cannot be empty" }
            Client.builder().endpoints(url).build()
                .use { client ->
                    client.withKvClient { kvClient ->
                        kvClient.delete(barrierPath)
                    }
                }
        }
    }
}