/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event.source.pool;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

public class InformerPool
    extends AbstractEventSourcePool<InformerClassifier, SharedIndexInformer<?>> {

  private final KubernetesClient client;

  private Map<InformerClassifier, SharedIndexInformer<?>> informers = new ConcurrentHashMap<>();
  private Map<SharedIndexInformer<?>, AtomicInteger> counters = new ConcurrentHashMap<>();

  public InformerPool(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public SharedIndexInformer<?> getEventSource(InformerClassifier classifier) {
    var actual = informers.get(classifier);
    if (actual == null) {
      actual = null; // create Informer
    }
    incrementCounter(actual);
    return null;
  }

  private synchronized void incrementCounter(SharedIndexInformer<?> actual) {
    counters.compute(actual, (k, v) -> new AtomicInteger(v == null ? 0 : v.incrementAndGet()));
  }

  @Override
  public void removeEventSource(SharedIndexInformer<?> informerEventSource) {}
}
