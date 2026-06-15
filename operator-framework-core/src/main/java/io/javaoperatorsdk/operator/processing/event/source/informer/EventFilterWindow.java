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
 * Contains all the relevant information around the eventing and algorithms of a single resources.
 */
class EventFilterWindow {

  private static final Logger log = LoggerFactory.getLogger(EventFilterWindow.class);

  private final SortedMap<Long, GenericResourceEvent> relatedEvents = new TreeMap<>();
  private final SortedSet<Long> ownResourceVersions = new TreeSet<>();
  private boolean reListOnGoing;
  private int activeUpdates = 0;

  public EventFilterWindow(boolean reListOnGoing) {
    this.reListOnGoing = reListOnGoing;
  }

  // Before we run this method
  // - we continuously process incoming events from the informer
  // - we record the resource version of the updated resources for our own writes
  // The goal:
  // - is to filter out events for which we are sure that results of our own updates.
  //   - note that updates can happen before our updates and after or between two updates
  //     since we don't require optimistic locking
  // - if we have to emit an event we should make it equivalent to a real life like event
  //   and should be as wide as possible
  // - we receive events from informers, informers sometimes do relist.
  //   Meaning there might be events lost. But we have callback when that is going on.
  // - we should emit events as soon as possible, thus for example we have two parallel
  //   updates, we see that we have an additional event before our first update received but
  // recording
  //   already started. We should emit the synth event from this check method as soon as we received
  //   an event that has same resource version or newer as our resource
  public synchronized Optional<GenericResourceEvent> check() {
    String beforeState = log.isDebugEnabled() ? snapshotState() : null;
    Optional<GenericResourceEvent> result = doCheck();
    if (log.isDebugEnabled()) {
      log.debug(
          "check() input state: {} → outcome: {} → state after: {}",
          beforeState,
          result.map(GenericResourceEvent::toString).orElse("empty"),
          snapshotState());
    }
    return result;
  }

  private String snapshotState() {
    return String.format(
        "relatedEvents=%s, ownResourceVersions=%s, activeUpdates=%d, reListOnGoing=%s",
        relatedEvents.keySet(), ownResourceVersions, activeUpdates, reListOnGoing);
  }

  private Optional<GenericResourceEvent> doCheck() {
    if (relatedEvents.isEmpty()) {
      return Optional.empty();
    }
    if (activeUpdates == 0 && ownResourceVersions.isEmpty()) {
      return eventForRangeAndClear(relatedEvents, ownResourceVersions);
    }
    if (ownResourceVersions.isEmpty()
        && getFirstRelatedEvent().getAction().equals(ResourceAction.DELETED)) {
      return eventForRangeAndClear(relatedEvents, ownResourceVersions);
    }

    var lastEventVersion = relatedEvents.lastKey();
    var numberOwnUpdatesSelected = 0;
    long lastOwnVersion = -1;
    for (long ownVersion : ownResourceVersions) {
      if (ownVersion <= lastEventVersion) {
        numberOwnUpdatesSelected++;
        lastOwnVersion = ownVersion;
      } else {
        break;
      }
    }
    if (numberOwnUpdatesSelected > 0) {
      if (numberOwnUpdatesSelected == ownResourceVersions.size() && activeUpdates == 0) {
        return eventForRangeAndClear(relatedEvents, ownResourceVersions);
      } else {
        if (numberOwnUpdatesSelected < ownResourceVersions.size()) {
          return eventForRangeAndClear(
              relatedEvents.headMap(ownResourceVersions.tailSet(lastOwnVersion + 1).first()),
              ownResourceVersions.headSet(lastOwnVersion + 1));
        } else
          return eventForRangeAndClear(
              relatedEvents.headMap(lastOwnVersion + 1),
              ownResourceVersions.headSet(lastOwnVersion + 1));
      }
    }
    return Optional.empty();
  }

  // it has responsibility to clear those ranges and emit event if needed
  Optional<GenericResourceEvent> eventForRangeAndClear(
      SortedMap<Long, GenericResourceEvent> events, SortedSet<Long> ownResourceVersions) {
    if (events.isEmpty()) {
      return Optional.empty();
    }
    var isAnyEventFromReList =
        events.values().stream().anyMatch(GenericResourceEvent::isPartOfReList);

    var first = getFirstRelatedEvent(events);
    if (events.size() > 1 && first.getAction() == ResourceAction.DELETED) {
      events.remove(events.firstKey());
      first = getFirstRelatedEvent(events);
    }

    if (events.keySet().equals(ownResourceVersions) && !isAnyEventFromReList) {
      GenericResourceEvent res = null;
      var lastEvent = getLastRelatedEvent(events);
      if (lastEvent.getAction() == ResourceAction.DELETED) {
        res = lastEvent;
      }
      events.clear();
      ownResourceVersions.clear();
      return Optional.ofNullable(res);
    }

    if (events.size() == 1) {
      ownResourceVersions.clear();
      var res = Optional.of(events.values().iterator().next());
      events.clear();
      return res;
    }
    var lastEvent = getLastRelatedEvent(events);
    if (lastEvent.getAction() == ResourceAction.DELETED) {
      events.clear();
      ownResourceVersions.clear();
      return Optional.of(lastEvent);
    }

    var res =
        Optional.of(
            new GenericResourceEvent(
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

  private GenericResourceEvent getFirstRelatedEvent() {
    return getFirstRelatedEvent(relatedEvents);
  }

  private GenericResourceEvent getFirstRelatedEvent(SortedMap<Long, GenericResourceEvent> subMap) {
    return subMap.values().iterator().next();
  }

  private GenericResourceEvent getLastRelatedEvent(SortedMap<Long, GenericResourceEvent> subMap) {
    return subMap.get(subMap.lastKey());
  }

  private GenericResourceEvent getLastRelatedEvent() {
    return getLastRelatedEvent(relatedEvents);
  }

  public synchronized boolean canBeRemoved() {
    if (activeUpdates == 0 && ownResourceVersions.isEmpty() && relatedEvents.isEmpty()) {
      return true;
    }
    return false;
  }

  public synchronized void addToOwnResourceVersions(String resourceVersion) {
    ownResourceVersions.add(Long.parseLong(resourceVersion));
  }

  public synchronized void addRelatedEvent(GenericResourceEvent event) {
    if (reListOnGoing) {
      event.setPartOfReList(true);
    }

    relatedEvents.put(
        Long.parseLong(event.getResource().orElseThrow().getMetadata().getResourceVersion()),
        event);
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

  synchronized SortedMap<Long, GenericResourceEvent> getRelatedEvents() {
    return relatedEvents;
  }

  synchronized SortedSet<Long> getOwnResourceVersions() {
    return ownResourceVersions;
  }
}
