/*
 * Copyright © 2019 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package io.etcd.recipes.election

import com.sudothought.common.util.random
import com.sudothought.common.util.sleep
import io.etcd.recipes.common.blockingThreads
import mu.KLogging
import org.amshove.kluent.shouldEqual
import org.junit.jupiter.api.Test
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.seconds

class ThreadedLeaderSelectorTests {
    val urls = listOf("http://localhost:2379")
    val path = "/election/${javaClass.simpleName}"
    val count = 10

    @Test
    fun threadedElection1Test() {
        val takeLeadershiptCounter = AtomicInteger(0)
        val relinquishLeadershiptCounter = AtomicInteger(0)

        blockingThreads(count) {
            val takeLeadershipAction =
                { selector: LeaderSelector ->
                    val pause = 3.random.seconds
                    logger.info { "${selector.clientId} elected leader for $pause" }
                    takeLeadershiptCounter.incrementAndGet()
                    sleep(pause)
                }

            val relinquishLeadershipAction =
                { selector: LeaderSelector ->
                    relinquishLeadershiptCounter.incrementAndGet()
                    logger.info { "${selector.clientId} relinquished leadership" }
                }

            LeaderSelector(urls, path, takeLeadershipAction, relinquishLeadershipAction, "Thread$it")
                .use { election ->
                    election.start()
                    election.waitOnLeadershipComplete()
                }
        }

        takeLeadershiptCounter.get() shouldEqual count
        relinquishLeadershiptCounter.get() shouldEqual count
    }

    @Test
    fun threadedElection2Test() {
        val takeLeadershiptCounter = AtomicInteger(0)
        val relinquishLeadershiptCounter = AtomicInteger(0)
        val electionList = Collections.synchronizedList(mutableListOf<LeaderSelector>())

        blockingThreads(count) {
            val takeLeadershipAction =
                { selector: LeaderSelector ->
                    val pause = 3.random.seconds
                    logger.info { "${selector.clientId} elected leader for $pause" }
                    takeLeadershiptCounter.incrementAndGet()
                    sleep(pause)
                }

            val relinquishLeadershipAction =
                { selector: LeaderSelector ->
                    relinquishLeadershiptCounter.incrementAndGet()
                    logger.info { "${selector.clientId} relinquished leadership" }
                }

            logger.info { "Creating Thread$it" }
            val election =
                LeaderSelector(urls, path, takeLeadershipAction, relinquishLeadershipAction, "Thread$it")
            electionList += election
            election.start()
        }

        logger.info { "Size = ${electionList.size}" }
        electionList
            .onEach { it.waitOnLeadershipComplete() }
            .forEach { it.close() }

        takeLeadershiptCounter.get() shouldEqual count
        relinquishLeadershiptCounter.get() shouldEqual count
    }

    companion object : KLogging()
}