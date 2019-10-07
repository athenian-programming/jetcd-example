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

package com.sudothought.etcdrecipes.common

import com.sudothought.common.delegate.AtomicDelegates
import io.etcd.jetcd.Client
import java.util.concurrent.Semaphore

open class EtcdConnector(url: String) {

    protected val semaphore = Semaphore(1, true)
    protected val client = lazy { Client.builder().endpoints(url).build() }
    protected val kvClient = lazy { client.value.kvClient }
    protected val leaseClient = lazy { client.value.leaseClient }
    protected val watchClient = lazy { client.value.watchClient }
    protected var closeCalled by AtomicDelegates.atomicBoolean(false)

    protected fun checkCloseNotCalled() {
        if (closeCalled) throw EtcdRecipeRuntimeException("close() already closed")
    }

    open fun close() {
        if (!closeCalled) {
            if (watchClient.isInitialized())
                watchClient.value.close()

            if (leaseClient.isInitialized())
                leaseClient.value.close()

            if (kvClient.isInitialized())
                kvClient.value.close()

            if (client.isInitialized())
                client.value.close()

            closeCalled = true
        }
    }
}