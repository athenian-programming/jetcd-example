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

import com.google.common.primitives.Ints
import com.google.common.primitives.Longs
import io.etcd.jetcd.ByteSequence

val String.asByteSequence: ByteSequence get() = ByteSequence.from(toByteArray())

val Int.asByteSequence: ByteSequence get() = ByteSequence.from(Ints.toByteArray(this))

val Long.asByteSequence: ByteSequence get() = ByteSequence.from(Longs.toByteArray(this))

val ByteSequence.asString: String get() = toString(Charsets.UTF_8)

val ByteSequence.asInt: Int get() = Ints.fromByteArray(bytes)

val ByteSequence.asLong: Long get() = Longs.fromByteArray(bytes)

val Pair<String, ByteSequence>.asString: Pair<String, String> get() = first to second.asString

val Pair<String, ByteSequence>.asInt: Pair<String, Int> get() = first to second.asInt

val Pair<String, ByteSequence>.asLong: Pair<String, Long> get() = first to second.asLong

val List<Pair<String, ByteSequence>>.asString: List<Pair<String, String>> get() = map { it.first to it.second.asString }

val List<Pair<String, ByteSequence>>.asInt: List<Pair<String, Int>> get() = map { it.first to it.second.asInt }

val List<Pair<String, ByteSequence>>.asLong: List<Pair<String, Long>> get() = map { it.first to it.second.asLong }