package org.athenian.election

import org.athenian.utils.sleep
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
fun main() {
    val url = "http://localhost:2379"
    val electionName = "/election/leaderElectionDemo"

    LeaderSelector.reset(url, electionName)

    val leadershipAction = { selector: LeaderSelector ->
        println("${selector.id} elected leader")
        val pause = Random.nextInt(5).seconds
        sleep(pause)
        println("${selector.id} surrendering after $pause")
    }

    LeaderSelector(url, electionName, leadershipAction)
        .use { election ->
            repeat(5) {
                election.start()
                election.await()
            }
        }

    repeat(5) {
        LeaderSelector(url, electionName, leadershipAction)
            .use { election ->
                election.start()
                election.await()
            }
    }
}
