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
 * Type of a recorded Kubernetes event.
 *
 * <p>Kubernetes models this field as a free form string, but only these two values are meaningful:
 * tooling filters on them, so a value outside this set produces an event that is silently ignored
 * by anything looking for warnings. Hence the closed set here, unlike the reason of an event, which
 * is domain specific and therefore a plain string.
 */
public enum EventType {
  NORMAL("Normal"),
  WARNING("Warning");

  private final String value;

  EventType(String value) {
    this.value = value;
  }

  /**
   * @return the value to use in the {@code type} field of a Kubernetes event
   */
  public String value() {
    return value;
  }
}
