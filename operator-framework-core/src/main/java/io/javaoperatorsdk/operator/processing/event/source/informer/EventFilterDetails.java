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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;

class EventFilterDetails {

  // initially should be created during event filtering update

  private int activeUpdates = 0;
  // todo might be just one
  private final List<ResourceEvent> events = new ArrayList<>();
  private int lastUpdatedResourceVersion = -1;

  public int getActiveUpdates() {
    return activeUpdates;
  }

  public void increaseActiveUpdates() {
    activeUpdates = activeUpdates + 1;
  }

  public void decreaseActiveUpdates() {
    activeUpdates = activeUpdates - 1;
  }

  public void recordEvent(ResourceEvent event) {
    events.add(event);
  }

  public void setLastUpdatedResourceVersion(String version) {
    var parsed = Integer.parseInt(version);
    if (parsed > lastUpdatedResourceVersion) {
      lastUpdatedResourceVersion = parsed;
    }
  }

  public Optional<ResourceEvent> getLatestEventAfterLastUpdateEvent() {
    if (events.isEmpty()) return Optional.empty();
    var latest = events.get(events.size() - 1);
    if (Integer.parseInt(latest.getResource().orElseThrow().getMetadata().getResourceVersion())
        > lastUpdatedResourceVersion) {
      return Optional.of(latest);
    } else {
      return Optional.empty();
    }
  }

  public boolean isFilteringDone() {
    return activeUpdates == 0;
  }
}
