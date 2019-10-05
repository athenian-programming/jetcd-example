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

package org.athenian.election

import com.sudothought.common.util.sleep
import kotlin.time.seconds

fun main() {
    val url = "http://localhost:2379"
    val electionName = "/election/single"

    LeaderSelector.reset(url, electionName)

    val leadershipAction = { selector: LeaderSelector ->
        println("${selector.clientId} elected leader")
        val pause = 0.seconds //Random.nextInt(1, 3).seconds
        sleep(pause)
        println("${selector.clientId} surrendering after $pause")
    }

    LeaderSelector(url, electionName, leadershipAction)
        .use { selector ->
            repeat(100) {
                println("Iteration $it")
                selector.start()
                selector.waitOnLeadershipComplete()
            }
        }

    repeat(5) {
        LeaderSelector(url, electionName, leadershipAction)
            .use { selector ->
                println("Iteration $it")
                selector.start()
                selector.waitOnLeadershipComplete()
            }
    }
}