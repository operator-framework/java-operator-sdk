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

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

/**
 * Contains all the relevant information around the eventing and algorithms of a single resources.
 */
class EventingDetail {

  private static final Logger log = LoggerFactory.getLogger(EventingDetail.class);

  private final SortedMap<Long, GenericResourceEvent> relatedEvents = new TreeMap<>();
  private final SortedSet<Long> ownResourceVersions = new TreeSet<>();
  private Long lastResourceVersionBeforeReList;
  private int activeUpdates = 0;
  private boolean ownRvEverAdded = false;
  private Long lastEmittedResourceRv;

  public EventingDetail(Long lastResourceVersionBeforeReList) {
    this.lastResourceVersionBeforeReList = lastResourceVersionBeforeReList;
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
    if (relatedEvents.isEmpty()) {
      return Optional.empty();
    }

    boolean foundForeign = false;
    for (var entry : relatedEvents.entrySet()) {
      if (!isOwnEcho(entry.getKey(), entry.getValue())) {
        foundForeign = true;
        break;
      }
    }

    // While an in-flight write hasn't yet recorded its own RV, an apparent
    // foreign event might still turn out to be our own echo once the write
    // completes — hold it instead of emitting.
    if (foundForeign && activeUpdates > ownResourceVersions.size()) {
      return Optional.empty();
    }

    long maxRelatedRv = relatedEvents.lastKey();
    Optional<GenericResourceEvent> result = Optional.empty();

    // Emit if there is a foreign event in the window, or if a previously emitted
    // event already advanced the reconciler's view past some RV and a fresh own
    // echo now moves it further — the reconciler needs the catch-up.
    boolean shouldEmit =
        foundForeign || (lastEmittedResourceRv != null && maxRelatedRv > lastEmittedResourceRv);

    if (shouldEmit) {
      var firstEvent = relatedEvents.get(relatedEvents.firstKey());
      var lastEvent = relatedEvents.get(maxRelatedRv);
      if (relatedEvents.size() == 1) {
        result = Optional.of(firstEvent);
      } else if (lastEvent.getAction() == ResourceAction.DELETED) {
        result = Optional.of(lastEvent);
      } else {
        HasMetadata previous =
            firstEvent
                .getPreviousResource()
                .orElseGet(() -> firstEvent.getResource().orElseThrow());
        HasMetadata latest = lastEvent.getResource().orElseThrow();
        result =
            Optional.of(new GenericResourceEvent(ResourceAction.UPDATED, latest, previous, null));
      }
      lastEmittedResourceRv = maxRelatedRv;
    }

    relatedEvents.clear();
    ownResourceVersions.headSet(maxRelatedRv + 1).clear();
    return result;
  }

  private boolean isOwnEcho(Long resourceVersion, GenericResourceEvent event) {
    return event.getAction() == ResourceAction.UPDATED
        && ownResourceVersions.contains(resourceVersion);
  }

  public synchronized boolean canRemoved() {
    if (activeUpdates == 0 && ownResourceVersions.isEmpty() && ownRvEverAdded) {
      if (!relatedEvents.isEmpty()) {
        log.warn("Related events are not empty");
      }
      return true;
    }
    return false;
  }

  void addToOwnResourceVersions(String resourceVersion) {
    ownResourceVersions.add(Long.parseLong(resourceVersion));
    ownRvEverAdded = true;
  }

  public void addRelatedEvent(GenericResourceEvent event) {
    relatedEvents.put(
        Long.parseLong(event.getResource().orElseThrow().getMetadata().getResourceVersion()),
        event);
  }

  public synchronized void setReListStartedFrom(String lastResourceVersionBeforeReList) {
    this.lastResourceVersionBeforeReList = Long.parseLong(lastResourceVersionBeforeReList);
  }

  public synchronized void setReListFinished(String syncResourceVersion) {
    this.lastResourceVersionBeforeReList = null;
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
