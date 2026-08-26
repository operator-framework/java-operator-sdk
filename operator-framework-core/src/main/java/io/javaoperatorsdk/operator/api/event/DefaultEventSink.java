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
package io.javaoperatorsdk.operator.api.event;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

import static java.util.Objects.requireNonNullElse;

/**
 * Default {@link EventSink}, creating events in the {@code v1} (core) API group. The core group is
 * used rather than {@code events.k8s.io/v1} because it is what {@code kubectl describe} renders
 * uniformly and what the count based aggregation of the Kubernetes event model is defined on.
 *
 * <p>An event that does not exist yet is created. An event that does is a repeat of one already
 * recorded, since the name of an event is derived from everything that identifies it (see {@link
 * DefaultEventRecorder}), and is aggregated onto the recorded one by patching its {@code count} and
 * {@code lastTimestamp}. That is what makes {@code kubectl describe} report a repeating event once,
 * as {@code (x12 over 3m)}, rather than filling the event list with copies of it.
 *
 * <p>Recording therefore needs {@code get}, {@code create} and {@code patch} on {@code events} in
 * the core API group. The patch is made on the resource version the event was read at, so two
 * writers counting the same occurrence at the same time conflict, and the one that loses leaves the
 * occurrence uncounted and the conflict logged by {@link DefaultEventRecorder}, recording being
 * best effort.
 */
public class DefaultEventSink implements EventSink {

  private final KubernetesClient client;

  public DefaultEventSink(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public void emit(Event event) {
    var events = client.v1().events().inNamespace(event.getMetadata().getNamespace());
    var name = event.getMetadata().getName();
    var existing = events.withName(name).get();
    if (existing == null) {
      events.resource(event).create();
    } else {
      var aggregated =
          new EventBuilder(existing)
              .withCount(requireNonNullElse(existing.getCount(), 1) + 1)
              .withLastTimestamp(event.getLastTimestamp())
              .withMessage(event.getMessage())
              .build();
      events.withName(name).patch(aggregated);
    }
  }
}
