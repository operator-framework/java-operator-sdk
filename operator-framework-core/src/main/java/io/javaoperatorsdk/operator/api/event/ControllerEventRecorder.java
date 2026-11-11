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
 * An {@link EventRecorder} bound to a controller: events recorded through it are attributed to that
 * controller and assembled from its configuration, and can be about any object. This is the form to
 * use outside of a reconciliation, for example from a background task, see {@link
 * io.javaoperatorsdk.operator.RegisteredController#eventRecorder()}. Within a reconciliation,
 * prefer {@link io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder()} for events
 * about the primary resource and {@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder(HasMetadata)} for events about
 * another object.
 */
@Experimental(API_MIGHT_CHANGE)
public interface ControllerEventRecorder {

  /**
   * Records an event about the given object, attributed to the bound controller.
   *
   * @param regarding the object the event is about, referenced as its involved object
   * @param event the event to record
   */
  void record(HasMetadata regarding, EventRecord event);

  /**
   * Returns a view of this recorder bound to the given object as well, so that the object doesn't
   * have to be passed for every event.
   *
   * @param regarding the object subsequent events will be about
   * @return a recorder bound to the controller and to {@code regarding}
   */
  ResourceEventRecorder forResource(HasMetadata regarding);
}
