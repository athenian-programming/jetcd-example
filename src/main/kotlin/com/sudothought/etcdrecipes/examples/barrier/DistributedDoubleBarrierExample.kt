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

package com.sudothought.etcdrecipes.examples.barrier

import com.sudothought.common.util.random
import com.sudothought.common.util.sleep
import com.sudothought.etcdrecipes.barrier.DistributedDoubleBarrier
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.seconds

fun main() {
    val urls = listOf("http://localhost:2379")
    val barrierPath = "/barriers/doublebarriertest"
    val count = 5
    val enterLatch = CountDownLatch(count - 1)
    val leaveLatch = CountDownLatch(count - 1)
    val doneLatch = CountDownLatch(count)

    DistributedDoubleBarrier.delete(urls, barrierPath)

    fun enterBarrier(id: Int, barrier: DistributedDoubleBarrier, retryCount: Int = 0) {
        sleep(10.random.seconds)

        repeat(retryCount) {
            println("#$id Waiting to enter barrier")
            barrier.enter(2.seconds)
            println("#$id Timed out entering barrier")
        }

        enterLatch.countDown()
        println("#$id Waiting to enter barrier")
        barrier.enter()
        println("#$id Entered barrier")
    }

    fun leaveBarrier(id: Int, barrier: DistributedDoubleBarrier, retryCount: Int = 0) {
        sleep(10.random.seconds)

        repeat(retryCount) {
            println("#$id Waiting to leave barrier")
            barrier.leave(2.seconds)
            println("#$id Timed out leaving barrier")
        }

        leaveLatch.countDown()
        println("#$id Waiting to leave barrier")
        barrier.leave()
        println("#$id Left barrier")

        doneLatch.countDown()
    }

    repeat(count - 1) { i ->
        thread {
            DistributedDoubleBarrier(urls, barrierPath, count)
                .use { barrier ->
                    enterBarrier(i, barrier, 2)
                    sleep(5.random.seconds)
                    leaveBarrier(i, barrier, 2)
                }
        }
    }

    DistributedDoubleBarrier(urls, barrierPath, count)
        .use { barrier ->
            enterLatch.await()
            sleep(2.seconds)
            enterBarrier(99, barrier)

            leaveLatch.await()
            sleep(2.seconds)
            leaveBarrier(99, barrier)
        }

    doneLatch.await()

    println("Done")
}