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

package org.athenian.jetcd

import io.etcd.jetcd.ByteSequence
import io.etcd.jetcd.KV
import io.etcd.jetcd.kv.DeleteResponse
import io.etcd.jetcd.kv.GetResponse
import io.etcd.jetcd.kv.PutResponse
import io.etcd.jetcd.op.CmpTarget
import io.etcd.jetcd.options.GetOption
import io.etcd.jetcd.options.PutOption

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

private val String.asPrefixGetOption get() = GetOption.newBuilder().withPrefix(asByteSequence).build()

fun KV.getChildrenKeys(keyname: String): List<String> {
    val adjustedKey = keyname.ensureTrailing("/")
    return getKeys(adjustedKey, adjustedKey.asPrefixGetOption)
}

fun KV.getChildrenStringValues(keyname: String): List<String> {
    val adjustedKey = keyname.ensureTrailing("/")
    return getStringValues(adjustedKey, adjustedKey.asPrefixGetOption)
}

fun KV.getChildrenIntValues(keyname: String): List<Int> {
    val adjustedKey = keyname.ensureTrailing("/")
    return getIntValues(adjustedKey, adjustedKey.asPrefixGetOption)
}

fun KV.getChildrenLongValues(keyname: String): List<Long> {
    val adjustedKey = keyname.ensureTrailing("/")
    return getLongValues(adjustedKey, adjustedKey.asPrefixGetOption)
}

fun KV.getChildrenKVs(keyname: String): List<Pair<String, ByteSequence>> {
    val adjustedKey = keyname.ensureTrailing("/")
    return getKVs(adjustedKey, adjustedKey.asPrefixGetOption)
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

fun KV.getIntValues(keyname: String, getOption: GetOption = GetOption.DEFAULT): List<Int> =
    getResponse(keyname, getOption).kvs.map { it.value.asInt }

fun KV.getLongValues(keyname: String, getOption: GetOption = GetOption.DEFAULT): List<Long> =
    getResponse(keyname, getOption).kvs.map { it.value.asLong }

fun KV.getKVs(keyname: String, getOption: GetOption = GetOption.DEFAULT): List<Pair<String, ByteSequence>> =
    getResponse(keyname, getOption).kvs.map { it.key.asString to it.value }

fun Lazy<KV>.count(keyname: String): Long = value.count(keyname)
fun KV.count(keyname: String): Long {
    val adjustedKey = keyname.ensureTrailing("/")
    return getResponse(adjustedKey,
                       GetOption.newBuilder()
                           .withPrefix(adjustedKey.asByteSequence)
                           .withCountOnly(true)
                           .build()).count
}

fun KV.keyExists(keyname: String): Boolean = !(transaction { If(equalTo(keyname, CmpTarget.version(0))) }.isSucceeded)