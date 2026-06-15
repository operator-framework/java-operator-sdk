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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

class EventFilterSupport {

  private static final Logger log = LoggerFactory.getLogger(EventFilterSupport.class);

  private final Map<ResourceID, EventFilterWindow> eventFilterWindows = new HashMap<>();
  private boolean ongoingReList = false;

  public synchronized void startEventFilteringModify(ResourceID resourceID) {
    var existing = eventFilterWindows.get(resourceID);
    var ed =
        eventFilterWindows.computeIfAbsent(resourceID, id -> new EventFilterWindow(ongoingReList));
    ed.increaseActiveUpdates();
    log.debug(
        "startEventFilteringModify: id={}, windowReused={}, ongoingReList={}",
        resourceID,
        existing != null,
        ongoingReList);
  }

  public synchronized Optional<GenericResourceEvent> doneEventFilterModify(ResourceID resourceID) {
    var ed = eventFilterWindows.get(resourceID);
    if (ed == null) {
      log.debug("doneEventFilterModify: no window for id={}", resourceID);
      return Optional.empty();
    }
    ed.decreaseActiveUpdates();
    log.debug("doneEventFilterModify: id={}", resourceID);
    return check(ed, resourceID);
  }

  public synchronized Optional<GenericResourceEvent> processEvent(
      ResourceID resourceId, GenericResourceEvent genericResourceEvent) {
    var ed = eventFilterWindows.get(resourceId);
    if (ed != null) {
      log.debug(
          "processEvent: buffering event in window. id={}, action={}, rv={}",
          resourceId,
          genericResourceEvent.getAction(),
          genericResourceEvent
              .getResource()
              .map(r -> r.getMetadata().getResourceVersion())
              .orElse("?"));
      ed.addRelatedEvent(genericResourceEvent);
      return check(ed, resourceId);
    } else {
      log.debug(
          "processEvent: no active window, surfacing directly. id={}, action={}",
          resourceId,
          genericResourceEvent.getAction());
      return Optional.of(genericResourceEvent);
    }
  }

  private Optional<GenericResourceEvent> check(
      EventFilterWindow eventFilterWindow, ResourceID resourceID) {
    var res = eventFilterWindow.check();
    if (eventFilterWindow.canBeRemoved()) {
      log.debug("Removing empty event filter window. id={}", resourceID);
      eventFilterWindows.remove(resourceID);
    }
    return res;
  }

  public synchronized void addToOwnResourceVersions(ResourceID resourceId, String resourceVersion) {
    var window = eventFilterWindows.get(resourceId);
    if (window != null) {
      log.debug("Recording own resourceVersion. id={}, rv={}", resourceId, resourceVersion);
      window.addToOwnResourceVersions(resourceVersion);
    } else {
      log.debug(
          "addToOwnResourceVersions: no active window for id={}, rv={} (skipped)",
          resourceId,
          resourceVersion);
    }
  }

  public synchronized void handleGhostResourceRemoval(ResourceID resourceId) {
    log.debug("Ghost resource removal: discarding event filter window. id={}", resourceId);
    eventFilterWindows.remove(resourceId);
  }

  // for testing purposes
  synchronized Map<ResourceID, EventFilterWindow> getEventFilterWindows() {
    return eventFilterWindows;
  }

  public synchronized void setStartingReList() {
    log.debug("ReList starting: tagging {} active window(s)", eventFilterWindows.size());
    ongoingReList = true;
    eventFilterWindows.values().forEach(EventFilterWindow::setReListStarted);
  }

  public synchronized void setRelistFinished() {
    log.debug("ReList finished: clearing tag from {} active window(s)", eventFilterWindows.size());
    ongoingReList = false;
    eventFilterWindows.values().forEach(EventFilterWindow::setReListFinished);
  }

  public synchronized boolean isActiveUpdateFor(ResourceID resourceId) {
    return eventFilterWindows.containsKey(resourceId);
  }
}
