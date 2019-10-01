package org.athenian.election

import org.athenian.sleep
import kotlin.random.Random
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
fun main() {
    val url = "http://localhost:2379"
    val electionName = "/singleClient"

    LeaderElection(url, electionName)
        .also {
            val actions =
                ElectionActions(
                    onInitComplete = { println("${it.id} initialized") },
                    onElected = {
                        println("${it.id} elected leader")
                        val pause = Random.nextInt(5).seconds
                        sleep(pause)
                        println("${it.id} surrendering after $pause")
                    },
                    onFailedElection = {
                        //println("$id failed to get elected")
                    },
                    onTermComplete = {
                        println("${it.id} completed")
                        sleep(2.seconds)
                    }
                )
            it.start(actions)
            it.await()
        }
}