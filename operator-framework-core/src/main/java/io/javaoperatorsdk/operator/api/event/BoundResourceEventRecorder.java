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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;

/**
 * A {@link ResourceEventRecorder} that records through an {@link EventRecorder} about a fixed
 * object on behalf of a fixed controller.
 */
record BoundResourceEventRecorder(
    EventRecorder delegate, HasMetadata regarding, ControllerConfiguration<?> controller)
    implements ResourceEventRecorder {

  BoundResourceEventRecorder {
    Objects.requireNonNull(delegate, "the event recorder to record through must not be null");
    Objects.requireNonNull(regarding, "the object events are recorded about must not be null");
    Objects.requireNonNull(
        controller, "the configuration of the controller recording events must not be null");
  }

  @Override
  public void normal(String reason, String message) {
    record(EventRecord.normal(reason, message));
  }

  @Override
  public void warn(String reason, String message) {
    record(EventRecord.warning(reason, message));
  }

  @Override
  public void record(EventRecord event) {
    delegate.record(regarding, event, controller);
  }
}
