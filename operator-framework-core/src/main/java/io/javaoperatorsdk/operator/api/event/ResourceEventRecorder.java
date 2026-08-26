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

/**
 * An {@link EventRecorder} bound to a single object, typically the primary resource of the current
 * reconciliation.
 *
 * <p>Recording an event is best effort: failures to write the event to the cluster are logged and
 * swallowed, and never fail the caller.
 */
public interface ResourceEventRecorder {

  /** Records a {@link EventType#NORMAL} event about the bound object. */
  void normal(String reason, String message);

  /** Records a {@link EventType#WARNING} event about the bound object. */
  void warn(String reason, String message);

  /** Records the given event about the bound object. */
  void record(EventRecord event);
}
