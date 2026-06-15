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

import java.util.Objects;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;

/** Used only for resource event filtering. */
public class GenericResourceEvent extends ResourceEvent {

  private final HasMetadata previousResource;
  private final Boolean lastStateUnknow;
  private boolean partOfReList = false;

  public GenericResourceEvent(
      ResourceAction action,
      HasMetadata latestResource,
      HasMetadata previousResource,
      Boolean lastStateUnknow) {
    super(action, ResourceID.fromResource(latestResource), latestResource);
    this.previousResource = previousResource;
    this.lastStateUnknow = lastStateUnknow;
  }

  public Optional<HasMetadata> getPreviousResource() {
    return Optional.ofNullable(previousResource);
  }

  public Boolean isLastStateUnknow() {
    return lastStateUnknow;
  }

  @Override
  public String toString() {
    return "GenericResourceEvent{"
        + getPreviousResource()
            .map(r -> "previousResourceVersion=" + r.getMetadata().getResourceVersion())
            .orElse("")
        + ", action="
        + getAction()
        + getResource()
            .map(r -> ", resourceVersion=" + r.getMetadata().getResourceVersion())
            .orElse("")
        + ", relatedCustomResourceName="
        + getRelatedCustomResourceID().getName()
        + '}';
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    GenericResourceEvent that = (GenericResourceEvent) o;
    return Objects.equals(previousResource, that.previousResource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), previousResource);
  }

  public long getResourceVersion() {
    return Long.parseLong(getResource().orElseThrow().getMetadata().getResourceVersion());
  }

  public boolean isPartOfReList() {
    return partOfReList;
  }

  public void setPartOfReList(boolean partOfReList) {
    this.partOfReList = partOfReList;
  }
}
