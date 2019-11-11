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

package io.etcd.recipes.discovery

import io.etcd.jetcd.Client
import java.io.Closeable
import kotlin.random.Random

class ServiceProvider internal constructor(client: Client,
                                           namesPath: String,
                                           val serviceName: String) : Closeable {

    val serviceDiscovery = lazy { ServiceDiscovery(client, namesPath) }

    fun getInstance(): ServiceInstance = getAllInstances()[Random.nextInt(0, getAllInstances().size)]

    fun getAllInstances(): List<ServiceInstance> {
        return serviceDiscovery.value.queryForInstances(serviceName)
    }

    override fun close() {
        if (serviceDiscovery.isInitialized())
            serviceDiscovery.value.close()
    }
}