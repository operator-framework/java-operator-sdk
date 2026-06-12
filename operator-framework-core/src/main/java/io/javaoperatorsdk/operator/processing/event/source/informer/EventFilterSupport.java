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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class EventFilterSupport {

  private final Map<ResourceID, EventFilterWindow> eventFilterWindows = new HashMap<>();
  private Long lastKnownVersionBeforeRelist = null;

  public synchronized void startEventFilteringModify(ResourceID resourceID) {
    var ed =
        eventFilterWindows.computeIfAbsent(
            resourceID, id -> new EventFilterWindow(lastKnownVersionBeforeRelist));
    ed.increaseActiveUpdates();
  }

  public synchronized Optional<GenericResourceEvent> doneEventFilterModify(ResourceID resourceID) {
    var ed = eventFilterWindows.get(resourceID);
    if (ed == null) return Optional.empty();
    ed.decreaseActiveUpdates();
    return check(ed, resourceID);
  }

  public synchronized Optional<GenericResourceEvent> processEvent(
      ResourceID resourceId, GenericResourceEvent genericResourceEvent) {
    var ed = eventFilterWindows.get(resourceId);
    if (ed != null) {
      ed.addRelatedEvent(genericResourceEvent);
      return check(ed, resourceId);
    } else {
      return Optional.of(genericResourceEvent);
    }
  }

  private Optional<GenericResourceEvent> check(
      EventFilterWindow eventFilterWindow, ResourceID resourceID) {
    var res = eventFilterWindow.check();
    if (eventFilterWindow.canBeRemoved()) {
      eventFilterWindows.remove(resourceID);
    }
    return res;
  }

  public synchronized void addToOwnResourceVersions(ResourceID resourceId, String resourceVersion) {
    Optional.ofNullable(eventFilterWindows.get(resourceId))
        .ifPresent(au -> au.addToOwnResourceVersions(resourceVersion));
  }

  public synchronized void handleGhostResourceRemoval(ResourceID resourceId) {
    eventFilterWindows.remove(resourceId);
  }

  // for testing purposes
  synchronized Map<ResourceID, EventFilterWindow> getEventFilterWindows() {
    return eventFilterWindows;
  }

  public synchronized void setStartingReList(String lastKnownVersion) {
    lastKnownVersionBeforeRelist = Long.parseLong(lastKnownVersion);
    eventFilterWindows.values().forEach(au -> au.setReListStartedFrom(lastKnownVersion));
  }

  public synchronized void setRelistFinished(String syncResourceVersions) {
    lastKnownVersionBeforeRelist = null;
    eventFilterWindows.values().forEach(EventFilterWindow::setReListFinished);
  }

  public synchronized boolean isActiveUpdateFor(ResourceID resourceId) {
    return eventFilterWindows.containsKey(resourceId);
  }
}
