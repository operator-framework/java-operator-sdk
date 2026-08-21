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
package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.NamespaceChangeable;
import io.javaoperatorsdk.operator.api.events.EventRecorder;
import io.javaoperatorsdk.operator.health.ControllerHealthInfo;

public interface RegisteredController<P extends HasMetadata> extends NamespaceChangeable {

  ControllerConfiguration<P> getConfiguration();

  ControllerHealthInfo getControllerHealthInfo();

  /**
   * Returns the {@link EventRecorder} of this controller, to record Kubernetes events outside of a
   * reconciliation, for example from a status listener or a background task. Within a
   * reconciliation, use {@link io.javaoperatorsdk.operator.api.reconciler.Context#eventRecorder()}
   * instead.
   *
   * @return the event recorder associated with this controller
   */
  default EventRecorder eventRecorder() {
    throw new UnsupportedOperationException(
        "This implementation of RegisteredController does not provide an EventRecorder");
  }
}
