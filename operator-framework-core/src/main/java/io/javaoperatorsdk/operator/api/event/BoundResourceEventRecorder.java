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
import java.util.function.Consumer;

/**
 * A {@link ResourceEventRecorder} that hands every event to a fixed recording, through which an
 * {@link EventRecorder} binds it to an object and a reconciliation.
 */
record BoundResourceEventRecorder(Consumer<EventRecord> recording)
    implements ResourceEventRecorder {

  BoundResourceEventRecorder {
    Objects.requireNonNull(recording, "the recording to hand events to must not be null");
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
    recording.accept(event);
  }
}
