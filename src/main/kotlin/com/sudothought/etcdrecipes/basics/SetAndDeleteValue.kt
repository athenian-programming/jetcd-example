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

package com.sudothought.etcdrecipes.basics

import com.sudothought.common.util.repeatWithSleep
import com.sudothought.common.util.sleep
import com.sudothought.etcdrecipes.jetcd.delete
import com.sudothought.etcdrecipes.jetcd.getStringValue
import com.sudothought.etcdrecipes.jetcd.putValue
import com.sudothought.etcdrecipes.jetcd.withKvClient
import io.etcd.jetcd.Client
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.seconds

fun main() {
    val urls = listOf("http://localhost:2379")
    val path = "/foo"
    val keyval = "foobar"
    val countdown = CountDownLatch(2)

    thread {
        try {
            sleep(3.seconds)

            Client.builder().endpoints(*urls.toTypedArray()).build()
                .use { client ->
                    client.withKvClient { kvClient ->
                        println("Assigning $path = $keyval")
                        kvClient.putValue(path, keyval)

                        sleep(5.seconds)

                        println("Deleting $path")
                        kvClient.delete(path)
                    }
                }
        } finally {
            countdown.countDown()
        }
    }

    thread {
        try {
            Client.builder().endpoints(*urls.toTypedArray()).build()
                .use { client ->
                    client.withKvClient { kvClient ->
                        repeatWithSleep(12) { _, start ->
                            val respval = kvClient.getStringValue(path, "unset")
                            println("Key $path = $respval after ${System.currentTimeMillis() - start}ms")
                        }
                    }
                }

        } finally {
            countdown.countDown()
        }
    }

    countdown.await()
}
