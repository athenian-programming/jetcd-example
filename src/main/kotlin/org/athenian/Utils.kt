package org.athenian

import com.google.common.primitives.Ints
import com.google.common.primitives.Longs
import io.etcd.jetcd.Auth
import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.Client
import io.etcd.jetcd.Cluster
import io.etcd.jetcd.KV
import io.etcd.jetcd.Lease
import io.etcd.jetcd.Lock
import io.etcd.jetcd.Maintenance
import io.etcd.jetcd.Txn
import io.etcd.jetcd.Watch
import io.etcd.jetcd.kv.DeleteResponse
import io.etcd.jetcd.kv.GetResponse
import io.etcd.jetcd.kv.PutResponse
import io.etcd.jetcd.kv.TxnResponse
import io.etcd.jetcd.lease.LeaseGrantResponse
import io.etcd.jetcd.lock.LockResponse
import io.etcd.jetcd.lock.UnlockResponse
import io.etcd.jetcd.op.Cmp
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.op.Op
import io.etcd.jetcd.options.DeleteOption
import io.etcd.jetcd.options.GetOption
import io.etcd.jetcd.options.PutOption
import io.etcd.jetcd.options.WatchOption
import io.etcd.jetcd.watch.WatchResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.days
import kotlin.time.hours
import kotlin.time.microseconds
import kotlin.time.milliseconds
import kotlin.time.minutes
import kotlin.time.nanoseconds
import kotlin.time.seconds

val Int.random: Int get() = Random.nextInt(this)

val Long.random: Long get() = Random.nextLong(this)

val String.asByteSequence: ByteSequence get() = ByteSequence.from(this.toByteArray())

val Int.asByteSequence: ByteSequence get() = ByteSequence.from(Ints.toByteArray(this))

val Long.asByteSequence: ByteSequence get() = ByteSequence.from(Longs.toByteArray(this))

val ByteSequence.asString: String get() = toString(Charsets.UTF_8)

val ByteSequence.asInt: Int get() = Ints.fromByteArray(bytes)

val ByteSequence.asLong: Long get() = Longs.fromByteArray(bytes)

val LeaseGrantResponse.asPutOption: PutOption get() = PutOption.newBuilder().withLeaseId(this.id).build()

fun KV.putValue(keyname: String, keyval: String): PutResponse = put(keyname.asByteSequence, keyval.asByteSequence).get()

fun KV.putValue(keyname: String, keyval: Int): PutResponse = put(keyname.asByteSequence, keyval.asByteSequence).get()

fun KV.putValue(keyname: String, keyval: Long): PutResponse = put(keyname.asByteSequence, keyval.asByteSequence).get()

fun Lazy<KV>.putValue(keyname: String, keyval: String, option: PutOption): PutResponse =
    value.putValue(keyname, keyval, option)

fun KV.putValue(keyname: String, keyval: String, option: PutOption): PutResponse =
    put(keyname.asByteSequence, keyval.asByteSequence, option).get()

fun Lazy<KV>.putValue(keyname: String, keyval: Int, option: PutOption): PutResponse =
    value.putValue(keyname, keyval, option)

fun KV.putValue(keyname: String, keyval: Int, option: PutOption): PutResponse =
    put(keyname.asByteSequence, keyval.asByteSequence, option).get()

fun Lazy<KV>.putValue(keyname: String, keyval: Long, option: PutOption): PutResponse =
    value.putValue(keyname, keyval, option)

fun KV.putValue(keyname: String, keyval: Long, option: PutOption): PutResponse =
    put(keyname.asByteSequence, keyval.asByteSequence, option).get()

fun KV.delete(vararg keynames: String) = keynames.forEach { delete(it) }

fun Lazy<KV>.delete(keyname: String): DeleteResponse = value.delete(keyname)

fun KV.delete(keyname: String): DeleteResponse = delete(keyname.asByteSequence).get()

fun Lazy<KV>.getResponse(keyname: String, option: GetOption = GetOption.DEFAULT): GetResponse =
    value.getResponse(keyname, option)

fun KV.getResponse(keyname: String, option: GetOption = GetOption.DEFAULT): GetResponse =
    get(keyname.asByteSequence, option).get()

fun Lazy<KV>.keyIsPresent(keyname: String): Boolean = value.keyIsPresent(keyname)

fun KV.keyIsPresent(keyname: String): Boolean = getStringValue(keyname) != null

fun Lazy<KV>.keyIsNotPresent(keyname: String): Boolean = value.keyIsNotPresent(keyname)

fun KV.keyIsNotPresent(keyname: String): Boolean = !keyIsPresent(keyname)

fun Lazy<KV>.getStringValue(keyname: String): String? = value.getStringValue(keyname)

fun KV.getChildrenKeys(keyname: String): List<String> {
    val adjustedKey = keyname.ensureTrailing("/")
    return getKeys(adjustedKey, GetOption.newBuilder().withPrefix(adjustedKey.asByteSequence).build())
}

fun KV.getChildrenStringValues(keyname: String): List<String> {
    val adjustedKey = keyname.ensureTrailing("/")
    return getStringValues(adjustedKey, GetOption.newBuilder().withPrefix(adjustedKey.asByteSequence).build())
}

fun KV.getStringValue(keyname: String): String? =
    getResponse(keyname).kvs.takeIf { it.isNotEmpty() }?.get(0)?.value?.asString

fun Lazy<KV>.getStringValue(keyname: String, defaultVal: String): String = value.getStringValue(keyname, defaultVal)

fun KV.getStringValue(keyname: String, defaultVal: String): String = getStringValue(keyname) ?: defaultVal

fun Lazy<KV>.getIntValue(keyname: String): Int? = value.getIntValue(keyname)

fun KV.getIntValue(keyname: String): Int? =
    getResponse(keyname).kvs.takeIf { it.isNotEmpty() }?.get(0)?.value?.asInt

fun KV.getIntValue(keyname: String, defaultVal: Int): Int = getIntValue(keyname) ?: defaultVal

fun Lazy<KV>.getLongValue(keyname: String): Long? = value.getLongValue(keyname)

fun KV.getLongValue(keyname: String): Long? =
    getResponse(keyname).kvs.takeIf { it.isNotEmpty() }?.get(0)?.value?.asLong

fun KV.getLongValue(keyname: String, defaultVal: Long): Long = getLongValue(keyname) ?: defaultVal

fun KV.getKeys(keyname: String, getOption: GetOption = GetOption.DEFAULT): List<String> =
    getResponse(keyname, getOption).kvs.map { it.key.asString }

fun KV.getStringValues(keyname: String, getOption: GetOption = GetOption.DEFAULT): List<String> =
    getResponse(keyname, getOption).kvs.map { it.value.asString }

fun Lazy<KV>.countChildren(keyname: String): Long = value.countChildren(keyname)

fun KV.countChildren(keyname: String): Long {
    val adjustedKey = keyname.ensureTrailing("/")
    return getResponse(adjustedKey,
                       GetOption.newBuilder()
                           .withPrefix(adjustedKey.asByteSequence)
                           .withCountOnly(true)
                           .build()).count
}

fun Lock.lock(keyname: String, leaseId: Long): LockResponse = lock(keyname.asByteSequence, leaseId).get()

fun Lock.unlock(keyname: String): UnlockResponse = unlock(keyname.asByteSequence).get()

fun deleteOp(keyname: String, option: DeleteOption = DeleteOption.DEFAULT): Op.DeleteOp =
    Op.delete(keyname.asByteSequence, option)

fun putOp(keyname: String, keyval: String, option: PutOption = PutOption.DEFAULT): Op.PutOp =
    putOp(keyname, keyval.asByteSequence, option)

fun puOp(keyname: String, keyval: Int, option: PutOption = PutOption.DEFAULT): Op.PutOp =
    putOp(keyname, keyval.asByteSequence, option)

fun putOp(keyname: String, keyval: Long, option: PutOption = PutOption.DEFAULT): Op.PutOp =
    putOp(keyname, keyval.asByteSequence, option)

fun putOp(keyname: String, keyval: ByteSequence, option: PutOption = PutOption.DEFAULT): Op.PutOp =
    Op.put(keyname.asByteSequence, keyval, option)

fun <T> equals(keyname: String, target: CmpTarget<T>): Cmp = Cmp(keyname.asByteSequence, Cmp.Op.EQUAL, target)

fun <T> less(keyname: String, target: CmpTarget<T>): Cmp = Cmp(keyname.asByteSequence, Cmp.Op.LESS, target)

fun <T> greater(keyname: String, target: CmpTarget<T>): Cmp = Cmp(keyname.asByteSequence, Cmp.Op.GREATER, target)

fun Client.withWatchClient(block: (watchClient: Watch) -> Unit) = watchClient.use { block(it) }

fun Client.withLeaseClient(block: (leaseClient: Lease) -> Unit) = leaseClient.use { block(it) }

fun Client.withLockClient(block: (lockClient: Lock) -> Unit) = lockClient.use { block(it) }

fun Client.withMaintClient(block: (maintClient: Maintenance) -> Unit) = maintenanceClient.use { block(it) }

fun Client.withClusterClient(block: (clusterClient: Cluster) -> Unit) = clusterClient.use { block(it) }

fun Client.withAuthrClient(block: (authClient: Auth) -> Unit) = authClient.use { block(it) }

fun Client.withKvClient(block: (kvClient: KV) -> Unit) = kvClient.use { block(it) }

fun Lazy<Watch>.watcher(keyname: String,
                        option: WatchOption = WatchOption.DEFAULT,
                        block: (WatchResponse) -> Unit): Watch.Watcher = value.watcher(keyname, option, block)

fun Watch.watcher(keyname: String,
                  option: WatchOption = WatchOption.DEFAULT,
                  block: (WatchResponse) -> Unit): Watch.Watcher =
    watch(keyname.asByteSequence, option) {
        block(it)
    }

fun Lazy<KV>.transaction(block: Txn.() -> Txn): TxnResponse = value.transaction(block)

fun KV.transaction(block: Txn.() -> Txn): TxnResponse =
    txn().run {
        block()
        commit()
    }.get()

fun <T> Semaphore.withLock(block: () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
}

@ExperimentalTime
fun repeatWithSleep(iterations: Int,
                    sleepTime: Duration = 1.seconds,
                    block: (count: Int, startMillis: Long) -> Unit) {
    val startMillis = System.currentTimeMillis()
    repeat(iterations) { i ->
        block(i, startMillis)
        sleep(sleepTime)
    }
}

@ExperimentalTime
fun sleep(sleepTime: Duration) = Thread.sleep(sleepTime.toLongMilliseconds())

fun String.ensureTrailing(extChar: String = "/"): String = "$this${if (endsWith(extChar)) "" else extChar}"

fun String.append(suffix: String, extChar: String = "/"): String = "${ensureTrailing(extChar)}$suffix"

val CountDownLatch.isDone: Boolean get() = count == 0L


@ExperimentalTime
fun timeUnitToDuration(value: Long, timeUnit: TimeUnit): Duration =
    when (timeUnit) {
        TimeUnit.MICROSECONDS -> value.microseconds
        TimeUnit.NANOSECONDS -> value.nanoseconds
        TimeUnit.MILLISECONDS -> value.milliseconds
        TimeUnit.SECONDS -> value.seconds
        TimeUnit.MINUTES -> value.minutes
        TimeUnit.HOURS -> value.hours
        TimeUnit.DAYS -> value.days
    }

private val charPool = ('a'..'z') + ('A'..'Z') + ('0'..'9')

fun randomId(length: Int = 10): String =
    (1..length)
        .map { Random.nextInt(0, charPool.size) }
        .map { i -> charPool[i] }
        .joinToString("")