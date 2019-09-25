package org.athenian.election

import io.etcd.jetcd.*
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.options.WatchOption
import io.etcd.jetcd.watch.WatchEvent
import org.athenian.*
import java.io.Closeable
import java.lang.Math.abs
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds
import kotlin.time.seconds

@ExperimentalTime
class LeaderElection(val url: String,
                     val electionTopicName: String = defaultElectionKeyName,
                     val id: String = "Unassigned:${abs(Random.nextInt())}") : Closeable {
    private val startCountdown = CountDownLatch(1)
    private val initCountDown = CountDownLatch(1)
    private val watchCountDown = CountDownLatch(1)
    private val executor = Executors.newFixedThreadPool(2)

    fun start(actions: ElectionActions): LeaderElection {
        executor.submit {
            Client.builder().endpoints(url).build()
                .use { client ->
                    client.leaseClient
                        .use { leaseClient ->
                            client.watchClient
                                .use { watchClient ->
                                    client.kvClient
                                        .use { kvclient ->
                                            val countdown = CountDownLatch(1)

                                            initCountDown.countDown()

                                            executor.submit {
                                                watchForLeadershipOpening(watchClient) {
                                                    attemptToBecomeLeader(actions, leaseClient, kvclient)
                                                }.use {
                                                    watchCountDown.await()
                                                }
                                            }

                                            // Give the watcher a chance to start
                                            sleep(2.seconds)

                                            attemptToBecomeLeader(actions, leaseClient, kvclient)

                                            countdown.await()
                                        }
                                }
                        }
                }
        }

        initCountDown.await()
        actions.onInitComplete.invoke()

        return this
    }

    fun await(duration: Duration = 0.milliseconds): Boolean =
        startCountdown.await(duration.toLongMilliseconds(), TimeUnit.MILLISECONDS)

    override fun close() {
        watchCountDown.countDown()
        startCountdown.countDown()
        sleep(1.seconds)
        executor.shutdown()
    }

    // This will not return until election failure or relinquishes leadership
    private fun attemptToBecomeLeader(actions: ElectionActions, leaseClient: Lease, kvclient: KV) {

        val uniqueToken = "$id:${abs(Random.nextInt())}"
        val lease = leaseClient.grant(1).get()

        kvclient.txn()
            .run {
                If(equals(electionTopicName, CmpTarget.version(0)))
                Then(put(electionTopicName, uniqueToken, lease.asPutOption))
                commit().get()
            }

        if (kvclient.getValue(electionTopicName) == uniqueToken) {
            leaseClient.keepAlive(lease.id,
                                  Observers.observer(
                                      { next -> /*println("KeepAlive next resp: $next")*/ },
                                      { err -> /*println("KeepAlive err resp: $err")*/ })
            ).use {
                actions.onElected.invoke()
            }
            actions.onTermComplete.invoke()
        } else {
            actions.onFailedElection.invoke()
        }
    }

    private fun watchForLeadershipOpening(watchClient: Watch, action: () -> Unit): Watch.Watcher {
        val watchOptions = WatchOption.newBuilder().withRevision(0).build()
        return watchClient.watch(electionTopicName.asByteSequence, watchOptions) { resp ->
            resp.events.forEach { event ->
                if (event.eventType == WatchEvent.EventType.DELETE) {
                    //println("$clientId executing action")
                    action.invoke()
                }
            }
        }
    }

    companion object {
        private val defaultElectionKeyName = "/election/leader"

        fun resetKeys(url: String, electionKeyName: String = defaultElectionKeyName) {
            Client.builder().endpoints(url).build()
                .use { client ->
                    client.kvClient
                        .use { kvclient ->
                            kvclient.delete(electionKeyName)
                        }
                }
        }
    }
}

typealias ElectionAction = () -> Unit

class ElectionActions(val onElected: ElectionAction = {},
                      val onTermComplete: ElectionAction = {},
                      val onFailedElection: ElectionAction = {},
                      val onInitComplete: ElectionAction = {})

