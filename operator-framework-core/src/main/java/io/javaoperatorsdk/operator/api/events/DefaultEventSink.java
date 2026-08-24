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
package io.javaoperatorsdk.operator.api.events;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.client.KubernetesClient;

/**
 * Default {@link EventSink}, creating events in the {@code v1} (core) API group. The core group is
 * used rather than {@code events.k8s.io/v1} because it is what {@code kubectl describe} renders
 * uniformly and what the count based aggregation of the Kubernetes event model is defined on.
 *
 * <p>An event is only created if it does not exist yet.Should another writer create the event
 * between the lookup and the create, the resulting conflict is left to the caller, which is
 * expected to treat recording as best effort.
 */
public class DefaultEventSink implements EventSink {

  private final KubernetesClient client;

  public DefaultEventSink(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public void emit(Event event) {
    var events = client.v1().events().inNamespace(event.getMetadata().getNamespace());
    var existing = events.withName(event.getMetadata().getName()).get();
    if (existing == null) {
      events.resource(event).create();
    }
  }
}
