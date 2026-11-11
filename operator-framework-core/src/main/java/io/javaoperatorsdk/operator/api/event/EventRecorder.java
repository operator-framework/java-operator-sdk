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
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

/**
 * Records Kubernetes events on behalf of a controller.
 *
 * <p>This is the unbound form of the API: an instance is shared by all the controllers of the
 * operator, and everything that varies between them - the object an event is about and the
 * configuration of the controller the event is attributed to and assembled from - is passed per
 * call. Implementations are therefore expected to be stateless and thread safe, and only have to
 * implement {@link #record(HasMetadata, EventRecord, ControllerConfiguration)}: the views bound to
 * an object or to a controller are default methods that delegate to it. To record events through an
 * implementation of your own, see {@link
 * io.javaoperatorsdk.operator.api.config.ConfigurationService#eventRecorder()}.
 *
 * <p>Within a reconciliation, use {@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder()}, which is already bound to
 * the primary resource, or {@link
 * io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder(HasMetadata)} for an event about
 * another object. Outside of a reconciliation, use {@link
 * io.javaoperatorsdk.operator.RegisteredController#eventRecorder()}, which is bound to the
 * controller.
 *
 * <p>Whether a failure to record an event fails the caller is up to the implementation, see {@link
 * DefaultEventRecorder} for the default.
 */
@Experimental(API_MIGHT_CHANGE)
@FunctionalInterface
public interface EventRecorder {

  /**
   * Records an event about the given object, attributed to the controller with the given
   * configuration and assembled from it.
   *
   * @param regarding the object the event is about, referenced as the involved object of the
   *     resulting event
   * @param event the event to record
   * @param controller the configuration of the controller recording the event
   */
  void record(HasMetadata regarding, EventRecord event, ControllerConfiguration<?> controller);

  /**
   * Returns a view of this recorder bound to the given object and controller, so that neither has
   * to be passed for every event. The controller bound view returned by {@link
   * #forController(ControllerConfiguration)} obtains its object bound views through this method, so
   * overriding it changes those as well.
   *
   * @param regarding the object subsequent events will be about
   * @param controller the configuration of the controller recording them
   * @return a recorder bound to {@code regarding} and {@code controller}
   */
  default ResourceEventRecorder forResource(
      HasMetadata regarding, ControllerConfiguration<?> controller) {
    return new BoundResourceEventRecorder(this, regarding, controller);
  }

  /**
   * Returns a view of this recorder bound to the given controller, to record events about any
   * object on its behalf, typically outside of a reconciliation.
   *
   * @param controller the configuration of the controller subsequent events are recorded for
   * @return a recorder bound to {@code controller}
   */
  default ControllerEventRecorder forController(ControllerConfiguration<?> controller) {
    return new BoundControllerEventRecorder(this, controller);
  }
}
