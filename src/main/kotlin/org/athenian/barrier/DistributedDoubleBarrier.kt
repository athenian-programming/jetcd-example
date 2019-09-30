package org.athenian.barrier

import io.etcd.jetcd.Client
import org.athenian.append
import org.athenian.delete
import org.athenian.getChildrenKeys
import org.athenian.randomId
import org.athenian.withKvClient
import java.io.Closeable
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.days

@ExperimentalTime
class DistributedDoubleBarrier(val url: String,
                               val barrierPath: String,
                               val memberCount: Int,
                               val id: String = "Client:${randomId(6)}") : Closeable {

    private val enterBarrier = DistributedBarrierWithCount(url, barrierPath.append("enter"), memberCount, id)
    private val leaveBarrier = DistributedBarrierWithCount(url, barrierPath.append("leave"), memberCount, id)

    init {
        require(url.isNotEmpty()) { "URL cannot be empty" }
        require(barrierPath.isNotEmpty()) { "Barrier path cannot be empty" }
        require(memberCount > 0) { "Member count must be > 0" }
    }

    val enterWaiterCount: Long get() = enterBarrier.waiterCount

    val leaveWaiterCount: Long get() = leaveBarrier.waiterCount

    fun enter() = enter(Long.MAX_VALUE.days)

    fun enter(duration: Duration): Boolean = enterBarrier.waitOnBarrier(duration)

    fun leave() = leave(Long.MAX_VALUE.days)

    fun leave(duration: Duration): Boolean = leaveBarrier.waitOnBarrier(duration)

    override fun close() {
        enterBarrier.close()
        leaveBarrier.close()
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