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
import java.util.function.UnaryOperator;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;

class EventFilterDetails {

  private int activeUpdates = 0;
  private ResourceEvent lastEvent;
  private String lastOwnUpdatedResourceVersion;

  public void increaseActiveUpdates() {
    activeUpdates = activeUpdates + 1;
  }

  /**
   * resourceVersion is needed for case when multiple parallel updates happening inside the
   * controller to prevent race condition and send event from {@link
   * ManagedInformerEventSource#eventFilteringUpdateAndCacheResource(HasMetadata, UnaryOperator)}
   */
  public boolean decreaseActiveUpdates(String updatedResourceVersion) {
    if (updatedResourceVersion != null
        && (lastOwnUpdatedResourceVersion == null
            || ReconcilerUtilsInternal.compareResourceVersions(
                    updatedResourceVersion, lastOwnUpdatedResourceVersion)
                > 0)) {
      lastOwnUpdatedResourceVersion = updatedResourceVersion;
    }

    activeUpdates = activeUpdates - 1;
    return activeUpdates == 0;
  }

  public void setLastEvent(ResourceEvent event) {
    lastEvent = event;
  }

  public Optional<ResourceEvent> getLatestEventAfterLastUpdateEvent() {
    if (lastEvent != null
        && (lastOwnUpdatedResourceVersion == null
            || ReconcilerUtilsInternal.compareResourceVersions(
                    lastEvent.getResource().orElseThrow().getMetadata().getResourceVersion(),
                    lastOwnUpdatedResourceVersion)
                > 0)) {
      return Optional.of(lastEvent);
    }
    return Optional.empty();
  }

  public int getActiveUpdates() {
    return activeUpdates;
  }
}
