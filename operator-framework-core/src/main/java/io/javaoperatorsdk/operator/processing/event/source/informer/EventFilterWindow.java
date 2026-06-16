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
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

/**
 * Contains all the relevant information around the event filtering algorithms of a single
 * resources.
 *
 * <p>How does it work:
 *
 * <ul>
 *   <li>we continuously process incoming events from the informer
 *   <li>we record the resource version of the updated resources for our own writes
 * </ul>
 *
 * <p>The goal:
 *
 * <ul>
 *   <li>is to filter out events for which we are sure that results of our own updates. Note that
 *       updates can happen before our updates and after or between two updates since we don't
 *       require optimistic locking.
 *   <li>if we have to emit an event we should make it equivalent to a real life like event and
 *       should be as wide as possible
 *   <li>we receive events from informers, informers sometimes do relist. Meaning there might be
 *       events lost. But we have callback when that is going on.
 *   <li>we should emit events as soon as possible, thus for example we have two parallel updates,
 *       we see that we have an additional event before our first update received but recording
 *       already started. We should emit the synth event from this check method as soon as we
 *       received an event that has same resource version or newer as our resource.
 * </ul>
 */
class EventFilterWindow {

  private static final Logger log = LoggerFactory.getLogger(EventFilterWindow.class);

  private final SortedMap<Long, ExtendedResourceEvent> relatedEvents = new TreeMap<>();
  private final SortedSet<Long> ownUpdateVersions = new TreeSet<>();
  private boolean reListOnGoing;
  private int activeUpdates = 0;

  public EventFilterWindow(boolean reListOnGoing) {
    this.reListOnGoing = reListOnGoing;
  }

  public synchronized Optional<ExtendedResourceEvent> check() {
    String beforeState = log.isDebugEnabled() ? snapshotState() : null;
    Optional<ExtendedResourceEvent> result = doCheck();
    if (log.isDebugEnabled()) {
      log.debug(
          "check() input state: {} → outcome: {} → state after: {}",
          beforeState,
          result.map(ExtendedResourceEvent::toString).orElse("empty"),
          snapshotState());
    }
    return result;
  }

  private String snapshotState() {
    return String.format(
        "relatedEvents=%s, ownResourceVersions=%s, activeUpdates=%d, reListOnGoing=%s",
        relatedEvents.keySet(), ownUpdateVersions, activeUpdates, reListOnGoing);
  }

  private Optional<ExtendedResourceEvent> doCheck() {
    // if we don't have related events we have nothing to mit
    if (relatedEvents.isEmpty()) {
      return Optional.empty();
    }
    // cleanup events which are not related to our updates
    if (activeUpdates == 0 && ownUpdateVersions.isEmpty()) {
      return eventForRangeAndClear(relatedEvents, ownUpdateVersions);
    }
    // this is a special case that if we receive a delete event we
    // early clean it up, since we don't do filtering for deletes
    if (ownUpdateVersions.isEmpty()
        && getFirstRelatedEvent().getAction().equals(ResourceAction.DELETED)) {
      return eventForRangeAndClear(relatedEvents, ownUpdateVersions);
    }
    var lastEventVersion = relatedEvents.lastKey();
    var numberOwnUpdatesSelected = 0;
    long lastOwnVersion = -1;
    // we find the last own update version for which we have event for
    // so those are the once we are going to clear our in this execution
    for (long ownVersion : ownUpdateVersions) {
      if (ownVersion <= lastEventVersion) {
        numberOwnUpdatesSelected++;
        lastOwnVersion = ownVersion;
      } else {
        break;
      }
    }
    if (numberOwnUpdatesSelected > 0) {
      // If we selected all own update versions we process the whole range.
      // We check also if there is no active update, since if there still is
      // an event might have come which is newer than own version what it is for the ongoing update.
      // So If we have own version [1,3] and events [1,2,3,4] and active updates = 0
      // we select all events [1,2,3,4] because for the active
      // update we might add own version 4.
      if (numberOwnUpdatesSelected == ownUpdateVersions.size() && activeUpdates == 0) {
        return eventForRangeAndClear(relatedEvents, ownUpdateVersions);
      } else {
        // if we select only a subset of own updates, we select related events
        // up to the next own version (what is not selected).
        // So If we have own updates version [1,3,5] and events [1,2,3,4]
        // we select all those events (also 4) that happened before own version 5
        // for which we don't have event yet.
        if (numberOwnUpdatesSelected < ownUpdateVersions.size()) {
          return eventForRangeAndClear(
              relatedEvents.headMap(ownUpdateVersions.tailSet(lastOwnVersion + 1).first()),
              ownUpdateVersions.headSet(lastOwnVersion + 1));
        } else
          // this is essentially when we numberOwnUpdatesSelected == ownUpdateVersions.size() but
          // with active update > 0. In that case we:
          // So If we have own version [1,3] and events [1,2,3,4]
          // we select only events [1,2,3] (so no 4), because for the active
          // update we might add own version 4.
          return eventForRangeAndClear(
              relatedEvents.headMap(lastOwnVersion + 1),
              ownUpdateVersions.headSet(lastOwnVersion + 1));
      }
    }
    return Optional.empty();
  }

  // calculates and clears events and own resources for a sorted range of events and own resources
  Optional<ExtendedResourceEvent> eventForRangeAndClear(
      SortedMap<Long, ExtendedResourceEvent> events, SortedSet<Long> ownResourceVersions) {

    if (events.isEmpty()) {
      return Optional.empty();
    }

    var lastEvent = getLastRelatedEvent(events);
    if (lastEvent.getAction() == ResourceAction.DELETED) {
      events.clear();
      ownResourceVersions.clear();
      return Optional.of(lastEvent);
    }

    // if any of the events is part of re-list (including first delete) we detect it
    var isAnyEventFromReList = false;
    for (var e : events.values()) {
      if (e.isPartOfReList()) {
        isAnyEventFromReList = true;
        break;
      }
    }

    var first = getFirstRelatedEvent(events);
    // if delete event is first in the row and more events we can discard that
    // since won't play role in synthesized (synt) event.
    if (events.size() > 1 && first.getAction() == ResourceAction.DELETED) {
      events.remove(events.firstKey());
      first = getFirstRelatedEvent(events);
    }

    // if all updates are related to own updates we don't return event.
    //
    if (events.keySet().equals(ownResourceVersions) && !isAnyEventFromReList) {
      events.clear();
      ownResourceVersions.clear();
      return Optional.empty();
    }

    // if only one event we return that
    if (events.size() == 1) {
      ownResourceVersions.clear();
      var res = Optional.of(events.values().iterator().next());
      events.clear();
      return res;
    }

    // if none above we create a synt event that contains from the oldest know resource
    // to the newest one. This is important to filters see the whole range
    var res =
        Optional.of(
            new ExtendedResourceEvent(
                ResourceAction.UPDATED,
                lastEvent.getResource().orElseThrow(),
                first.getPreviousResource().isEmpty()
                    ? first.getResource().orElseThrow()
                    : first.getPreviousResource().orElseThrow(),
                null));
    events.clear();
    ownResourceVersions.clear();
    return res;
  }

  private ExtendedResourceEvent getFirstRelatedEvent() {
    return getFirstRelatedEvent(relatedEvents);
  }

  private ExtendedResourceEvent getFirstRelatedEvent(
      SortedMap<Long, ExtendedResourceEvent> subMap) {
    return subMap.get(subMap.firstKey());
  }

  private ExtendedResourceEvent getLastRelatedEvent(SortedMap<Long, ExtendedResourceEvent> subMap) {
    return subMap.get(subMap.lastKey());
  }

  public synchronized boolean canBeRemoved() {
    if (activeUpdates == 0 && ownUpdateVersions.isEmpty() && relatedEvents.isEmpty()) {
      return true;
    }
    return false;
  }

  public synchronized void addToOwnUpdateVersions(String resourceVersion) {
    ownUpdateVersions.add(Long.valueOf(resourceVersion));
  }

  public synchronized void addRelatedEvent(ExtendedResourceEvent event) {
    if (reListOnGoing) {
      event.setPartOfReList(true);
    }

    relatedEvents.put(
        Long.valueOf(event.getResource().orElseThrow().getMetadata().getResourceVersion()), event);
  }

  public synchronized void setReListStarted() {
    reListOnGoing = true;
  }

  public synchronized void setReListFinished() {
    reListOnGoing = false;
  }

  public synchronized void increaseActiveUpdates() {
    activeUpdates++;
  }

  public synchronized void decreaseActiveUpdates() {
    activeUpdates--;
  }

  synchronized SortedMap<Long, ExtendedResourceEvent> getRelatedEvents() {
    return relatedEvents;
  }

  synchronized SortedSet<Long> getOwnResourceVersions() {
    return ownUpdateVersions;
  }
}
