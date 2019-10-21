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

package io.etcd.recipes.examples.basics;

import com.google.common.collect.Lists;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.Watch.Watcher;
import kotlin.Unit;

import java.util.List;

import static com.sudothought.common.util.Misc.sleepSecs;
import static io.etcd.recipes.common.ClientUtils.connectToEtcd;
import static io.etcd.recipes.common.KVUtils.count;
import static io.etcd.recipes.common.KVUtils.delete;
import static io.etcd.recipes.common.KVUtils.getKeyValues;
import static io.etcd.recipes.common.KVUtils.getKeys;
import static io.etcd.recipes.common.KVUtils.putValue;
import static io.etcd.recipes.common.KeyValueUtils.getAsString;
import static io.etcd.recipes.common.PairUtils.getAsString;
import static io.etcd.recipes.common.WatchUtils.getAsPrefixWatchOption;
import static io.etcd.recipes.common.WatchUtils.watcher;
import static java.lang.String.format;

public class WatchKeyRange {

    public static void main(String[] args) throws InterruptedException {
        List<String> urls = Lists.newArrayList("http://localhost:2379");
        String path = "/watchkeyrange";

        try (Client client = connectToEtcd(urls);
             KV kvClient = client.getKVClient();
             Watch watchClient = client.getWatchClient();
             Watcher watcher = watcher(watchClient,
                     path,
                     getAsPrefixWatchOption("/"),
                     (watchResponse) -> {
                         watchResponse.getEvents().forEach((watchEvent) -> {
                             System.out.println(format("%s for %s",
                                     watchEvent.getEventType(),
                                     getAsString(watchEvent.getKeyValue())
                             ));

                         });
                         return Unit.INSTANCE;
                     })) {

            // Create empty root
            putValue(kvClient, path, "root");

            System.out.println("After creation:");
            System.out.println(getAsString(getKeyValues(kvClient, path)));
            System.out.println(count(kvClient, path));

            sleepSecs(5);

            // Add children
            putValue(kvClient, path + "/election/a", "a");
            putValue(kvClient, path + "/election/b", "bb");
            putValue(kvClient, path + "/waiting/c", "ccc");
            putValue(kvClient, path + "/waiting/d", "dddd");

            System.out.println("\nAfter addition:");
            System.out.println(getAsString(getKeyValues(kvClient, path)));
            System.out.println(count(kvClient, path));

            System.out.println("\nElections only:");
            System.out.println(getAsString(getKeyValues(kvClient, path + "/election")));
            System.out.println(count(kvClient, path + "/election"));

            System.out.println("\nWaitings only:");
            System.out.println(getAsString(getKeyValues(kvClient, path + "/waiting")));
            System.out.println(count(kvClient, path + "/waiting"));

            sleepSecs(5);

            // Delete root
            delete(kvClient, path);

            // Delete children
            getKeys(kvClient, path).forEach((keyname) -> {
                System.out.println(format("Deleting key: %s", keyname));
                delete(kvClient, keyname);
            });

            System.out.println("\nAfter removal:");
            System.out.println(getAsString(getKeyValues(kvClient, path)));
            System.out.println(count(kvClient, path));

            sleepSecs(5);
        }
    }
}