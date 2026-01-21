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
package io.javaoperatorsdk.operator.processing.event.source.controller;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

/**
 * Extends ResourceEvent for informer Delete events, it holds also information if the final state is
 * unknown for the deleted resource.
 */
public class ResourceDeleteEvent extends ResourceEvent {

  private final boolean deletedFinalStateUnknown;

  public ResourceDeleteEvent(
      ResourceAction action,
      ResourceID resourceID,
      HasMetadata resource,
      boolean deletedFinalStateUnknown) {
    super(action, resourceID, resource);
    this.deletedFinalStateUnknown = deletedFinalStateUnknown;
  }

  public boolean isDeletedFinalStateUnknown() {
    return deletedFinalStateUnknown;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ResourceDeleteEvent that = (ResourceDeleteEvent) o;
    return deletedFinalStateUnknown == that.deletedFinalStateUnknown;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), deletedFinalStateUnknown);
  }
}
