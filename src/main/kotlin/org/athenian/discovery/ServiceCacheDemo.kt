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

package org.athenian.discovery

import com.sudothought.common.util.sleep
import kotlin.time.days


fun main() {

    val url = "http://localhost:2379"
    val serviceName = "/services/test"

    ServiceDiscovery<Int>(url, serviceName).use { sd ->

        sd.start()

        sd.serviceCache("TestName").use { cache ->

            cache.addListenerForChanges { eventType, serviceName, serviceInstance ->
                println("Change $eventType $serviceName $serviceInstance")
                println("Instances: ${cache.instances}")
            }
            cache.start()

            println("Waiting")
            sleep(2.days)
        }
    }
}