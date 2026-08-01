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
package io.javaoperatorsdk.operator.baseapi.informerpool.deregister;

/**
 * Spec of {@link DeregisterPrimaryCustomResource}; toggles the dynamic event source registration.
 */
public class DeregisterSpec {

  /**
   * When {@code true} the reconciler dynamically registers the event source for {@link
   * DeregisterWatchedCustomResource}; when {@code false} it de-registers it.
   */
  private boolean registerEventSource = true;

  public boolean isRegisterEventSource() {
    return registerEventSource;
  }

  public DeregisterSpec setRegisterEventSource(boolean registerEventSource) {
    this.registerEventSource = registerEventSource;
    return this;
  }
}
