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
package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;

/** Used only for resource event filtering. */
public class ExtendedResourceEvent extends ResourceEvent {

  private HasMetadata previousResource;

  public ExtendedResourceEvent(
      ResourceAction action,
      ResourceID resourceID,
      HasMetadata latestResource,
      HasMetadata previousResource) {
    super(action, resourceID, latestResource);
    this.previousResource = previousResource;
  }

  public Optional<HasMetadata> getPreviousResource() {
    return Optional.ofNullable(previousResource);
  }
}
