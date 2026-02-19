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
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

public class ResourceEvent extends Event {

  private final ResourceAction action;
  private final HasMetadata resource;

  public ResourceEvent(ResourceAction action, ResourceID resourceID, HasMetadata resource) {
    super(resourceID);
    this.action = action;
    this.resource = resource;
  }

  @Override
  public String toString() {
    return "ResourceEvent{"
        + "action="
        + action
        + ", associated resource id="
        + getRelatedCustomResourceID()
        + '}';
  }

  public ResourceAction getAction() {
    return action;
  }

  public Optional<HasMetadata> getResource() {
    return Optional.ofNullable(resource);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ResourceEvent that = (ResourceEvent) o;
    return action == that.action;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), action);
  }
}
