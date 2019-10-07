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

import io.etcd.jetcd.Lease
import io.etcd.jetcd.Observers
import io.etcd.jetcd.lease.LeaseGrantResponse
import io.etcd.jetcd.options.PutOption

val LeaseGrantResponse.asPutOption: PutOption get() = PutOption.newBuilder().withLeaseId(id).build()
fun Lease.keepAlive(lease: LeaseGrantResponse) =
    keepAlive(lease.id, Observers.observer(
        { /*println("KeepAlive next resp: $next")*/ },
        { /*println("KeepAlive err resp: $err")*/ }))

fun Lease.keepAliveWith(lease: LeaseGrantResponse, block: () -> Unit) =
    keepAlive(lease.id, Observers.observer(
        { /*next -> println("KeepAlive next resp: $next")*/ },
        { /*err -> println("KeepAlive err resp: $err")*/ }))
        .use {
            block.invoke()
        }