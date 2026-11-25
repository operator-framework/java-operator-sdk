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

import java.util.Objects;

import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import static java.util.Objects.requireNonNullElse;

/**
 * Default {@link EventSink}, creating events in the {@code v1} (core) API group. The core group is
 * used rather than {@code events.k8s.io/v1} because it is what {@code kubectl describe} renders
 * uniformly and what the count based aggregation of the Kubernetes event model is defined on.
 *
 * <p>An event that does not exist yet is created. An event that does is a repeat of one already
 * recorded, since the name of an event is derived from everything that identifies it (see {@link
 * DefaultEventRecorder}), and is aggregated onto the recorded one by patching its {@code count} and
 * {@code lastTimestamp}, along with its {@code message}, {@code type}, {@code reason} and {@code
 * action}, which events sharing a name explicitly may differ in, and its {@code involvedObject},
 * labels and annotations: they describe the latest occurrence, replacing what the recorded event
 * carried. The API server only makes event fields such as these, the {@code count} and the
 * timestamps immutable for {@code events.k8s.io/v1} requests, not for the core group this sink
 * writes to. An event that carries an {@code eventTime} needs an {@code action}, so the reason
 * stands in for it when the occurrence has none. That is what makes {@code kubectl describe} report
 * a repeating event once, as {@code (x12 over 3m)}, rather than filling the event list with copies
 * of it.
 *
 * <p>An explicitly named event can outlive the object it is about: an object deleted and recreated
 * under the same name records onto the event of its predecessor. An occurrence about a different
 * object than the recorded event, one with a different uid, switches the event to that object: the
 * event takes its owner references and counts its occurrences only, restarting its {@code count},
 * its {@code firstTimestamp} and, when the occurrence carries one, its {@code eventTime}, the same
 * way the default name, which hashes the uid, would start a new event. Occurrences about the same
 * object keep the owner references of the recorded event.
 *
 * <p>Recording needs {@code get}, {@code create} and {@code patch} on {@code events} in the core
 * API group. The patch is made on the resource version the event was read at, so two writers
 * counting the same occurrence at the same time conflict, and the one that loses leaves the
 * occurrence uncounted and the conflict to {@link DefaultEventRecorder} to handle. Where the API
 * server enforces owner reference permissions (the {@code OwnerReferencesPermissionEnforcement}
 * admission plugin, enabled by default on OpenShift), switching an event to a different object
 * changes its owner references if the recorded event or the occurrence carries any, which also
 * needs {@code delete} on {@code events}.
 */
public class DefaultEventSink implements EventSink {

  private final KubernetesClient client;

  public DefaultEventSink(KubernetesClient client) {
    this.client = client;
  }

  @Override
  public void emit(Event event, Context<?> context) {
    var events = client.v1().events().inNamespace(event.getMetadata().getNamespace());
    var name = event.getMetadata().getName();
    var existing = events.withName(name).get();
    if (existing == null) {
      events.resource(event).create();
    } else {
      var sameObject = Objects.equals(involvedObjectUid(existing), involvedObjectUid(event));
      var eventTime =
          sameObject || event.getEventTime() == null
              ? existing.getEventTime()
              : event.getEventTime();
      var action = event.getAction();
      if (action == null && eventTime != null) {
        action = event.getReason();
      }
      var aggregated =
          new EventBuilder(existing)
              .editMetadata()
              .withLabels(event.getMetadata().getLabels())
              .withAnnotations(event.getMetadata().getAnnotations())
              .withOwnerReferences(
                  sameObject
                      ? existing.getMetadata().getOwnerReferences()
                      : event.getMetadata().getOwnerReferences())
              .endMetadata()
              .withInvolvedObject(event.getInvolvedObject())
              .withCount(sameObject ? requireNonNullElse(existing.getCount(), 1) + 1 : 1)
              .withFirstTimestamp(
                  sameObject ? existing.getFirstTimestamp() : event.getFirstTimestamp())
              .withEventTime(eventTime)
              .withLastTimestamp(event.getLastTimestamp())
              .withMessage(event.getMessage())
              .withType(event.getType())
              .withReason(event.getReason())
              .withAction(action)
              .build();
      events.resource(existing).patch(aggregated);
    }
  }

  private static String involvedObjectUid(Event event) {
    var involvedObject = event.getInvolvedObject();
    return involvedObject != null ? involvedObject.getUid() : null;
  }
}
