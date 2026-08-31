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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Records Kubernetes events on behalf of a controller.
 *
 * <p>This is the unbound form of the API: it is scoped to a controller, not to a reconciliation,
 * and can therefore be used outside of the reconciliation loop, for example from a status listener
 * or a background task. To use it that way, configure the instance the operator records its events
 * through, see {@link io.javaoperatorsdk.operator.api.config.ConfigurationService#eventRecorder()},
 * and keep a reference to it. Within a reconciliation, prefer {@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder()}, which is already bound to
 * the primary resource.
 *
 * <p>Recording an event is best effort: failures to write the event to the cluster are logged and
 * swallowed, and never fail the caller.
 */
@Experimental(API_MIGHT_CHANGE)
public interface EventRecorder {

  /**
   * Records an event about the given object.
   *
   * @param regarding the object the event is about; it will be referenced as the involved object of
   *     the resulting event
   * @param event the event to record
   */
  void record(HasMetadata regarding, EventRecord event);

  /**
   * Returns a view of this recorder bound to the given object, so that the object doesn't have to
   * be passed for every event.
   *
   * @param regarding the object subsequent events will be about
   * @return a recorder bound to {@code regarding}
   */
  ResourceEventRecorder forResource(HasMetadata regarding);
}
