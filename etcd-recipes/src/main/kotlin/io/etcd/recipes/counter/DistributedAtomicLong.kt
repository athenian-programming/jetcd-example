/*
 * Copyright © 2021 Paul Ambrose (pambrose@mac.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("UndocumentedPublicClass", "UndocumentedPublicFunction")

package io.etcd.recipes.counter

import com.github.pambrose.common.util.*
import io.etcd.jetcd.*
import io.etcd.jetcd.kv.*
import io.etcd.jetcd.op.*
import io.etcd.recipes.common.*
import mu.*
import kotlin.time.Duration.Companion.milliseconds

@JvmOverloads
fun <T> withDistributedAtomicLong(
  client: Client,
  counterPath: String,
  default: Long = 0L,
  receiver: DistributedAtomicLong.() -> T
): T =
  DistributedAtomicLong(client, counterPath, default).use { it.receiver() }

class DistributedAtomicLong
@JvmOverloads
constructor(
  client: Client,
  val counterPath: String,
  private val default: Long = 0L
) : EtcdConnector(client) {

  init {
    require(counterPath.isNotEmpty()) { "Counter path cannot be empty" }

    // Create counter if first time through
    createCounterIfNotPresent()
  }

  @Synchronized
  fun get(): Long {
    checkCloseNotCalled()
    return client.getValue(counterPath, -1L)
  }

  fun increment(): Long = modifyCounterValue(1L)

  fun decrement(): Long = modifyCounterValue(-1L)

  fun add(value: Long): Long = modifyCounterValue(value)

  fun subtract(value: Long): Long = modifyCounterValue(-value)

  @Synchronized
  private fun modifyCounterValue(value: Long): Long {
    checkCloseNotCalled()
    var count = 1
    //totalCount.incrementAndGet()
    do {
      val txnResponse = applyCounterTransaction(value)
      if (!txnResponse.isSucceeded) {
        //println("Collisions: ${collisionCount.incrementAndGet()} Total: ${totalCount.get()} $count")
        // Crude backoff for retry
        sleep((count * 100).random().milliseconds)
        count++
      }
    } while (!txnResponse.isSucceeded)

    // Return the latest value
    return client.getValue(counterPath, -1L)
  }

  private fun createCounterIfNotPresent(): Boolean =
    // Run the transaction if the counter is not present
    if (client.getResponse(counterPath).kvs.isEmpty())
      client.transaction {
        If(counterPath.doesNotExist)
        Then(counterPath setTo default)
      }.isSucceeded
    else
      false

  private fun applyCounterTransaction(amount: Long): TxnResponse =
    client.transaction {
      val kvList: List<KeyValue> = client.getResponse(counterPath).kvs
      check(kvList.isNotEmpty()) { "Empty KeyValue list" }
      val kv = kvList.first()
      If(equalTo(counterPath, CmpTarget.modRevision(kv.modRevision)))
      Then(counterPath setTo kv.value.asLong + amount)
    }

  companion object : KLogging() {
    //val collisionCount = AtomicLong()
    //val totalCount = AtomicLong()

    @JvmStatic
    fun delete(client: Client, counterPath: String) {
      require(counterPath.isNotEmpty()) { "Counter path cannot be empty" }
      client.deleteKey(counterPath)
    }
  }
}