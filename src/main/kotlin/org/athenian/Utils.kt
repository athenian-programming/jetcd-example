package org.athenian

import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.KV
import io.etcd.jetcd.Lock
import io.etcd.jetcd.kv.DeleteResponse
import io.etcd.jetcd.kv.GetResponse
import io.etcd.jetcd.kv.PutResponse
import io.etcd.jetcd.lease.LeaseGrantResponse
import io.etcd.jetcd.lock.LockResponse
import io.etcd.jetcd.lock.UnlockResponse
import io.etcd.jetcd.op.Cmp
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.op.Op
import io.etcd.jetcd.options.PutOption
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

val String.asByteSequence: ByteSequence
    get() = ByteSequence.from(this.toByteArray())

val ByteSequence.asString: String
    get() = toString(Charsets.UTF_8)

val LeaseGrantResponse.asPutOption: PutOption
    get() = PutOption.newBuilder().withLeaseId(this.id).build()

@ExperimentalTime
fun repeatWithSleep(iterations: Int,
                    duration: Duration = 1.seconds,
                    block: (count: Int, startMillis: Long) -> Unit) {
    val startMillis = System.currentTimeMillis()
    repeat(iterations) { i ->
        block(i, startMillis)
        sleep(duration)
    }
}

@ExperimentalTime
fun sleep(duration: Duration) {
    Thread.sleep(duration.toLongMilliseconds())
}

infix fun KV.put(kv: Pair<String, String>): PutResponse = put(kv.first, kv.second)

fun KV.put(keyname: String, keyval: String): PutResponse = put(keyname.asByteSequence, keyval.asByteSequence).get()

fun KV.put(keyname: String, keyval: String, option: PutOption): PutResponse =
    put(keyval.asByteSequence, keyval.asByteSequence, option).get()

fun KV.delete(vararg keynames: String) = keynames.forEach { delete(it) }

infix fun KV.delete(keyname: String): DeleteResponse = delete(keyname.asByteSequence).get()

infix fun KV.get(keyname: String): GetResponse = get(keyname.asByteSequence).get()

infix fun KV.getValue(keyname: String): String? = get(keyname).kvs.takeIf { it.size > 0 }?.get(0)?.value?.asString

fun KV.getValue(keyname: String, defaultStr: String = ""): String = getValue(keyname) ?: defaultStr

fun Lock.lock(keyname: String, leaseId: Long): LockResponse = lock(keyname.asByteSequence, leaseId).get()

fun Lock.unlock(keyname: String): UnlockResponse = unlock(keyname.asByteSequence).get()

fun put(keyname: String, keyval: String, option: PutOption = PutOption.DEFAULT): Op.PutOp =
    Op.put(keyname.asByteSequence, keyval.asByteSequence, option)

fun <T> equals(keyname: String, target: CmpTarget<T>): Cmp = Cmp(keyname.asByteSequence, Cmp.Op.EQUAL, target)

fun <T> less(keyname: String, target: CmpTarget<T>): Cmp = Cmp(keyname.asByteSequence, Cmp.Op.LESS, target)

fun <T> greater(keyname: String, target: CmpTarget<T>): Cmp = Cmp(keyname.asByteSequence, Cmp.Op.GREATER, target)