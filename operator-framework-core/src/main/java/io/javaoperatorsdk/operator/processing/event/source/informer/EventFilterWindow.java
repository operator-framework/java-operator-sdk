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
class EventFilterWindow {

  private static final Logger log = LoggerFactory.getLogger(EventFilterWindow.class);

  private final SortedMap<Long, GenericResourceEvent> relatedEvents = new TreeMap<>();
  private final SortedSet<Long> ownResourceVersions = new TreeSet<>();
  private Long lastResourceVersionBeforeReList;
  private boolean affectedByReList;
  private int activeUpdates = 0;
  private boolean ownRvEverAdded = false;
  private int ownRvCount = 0;
  private Long lastEmittedResourceRv;
  private Long lastSeenRelatedRv;

  public EventFilterWindow(Long lastResourceVersionBeforeReList) {
    this.lastResourceVersionBeforeReList = lastResourceVersionBeforeReList;
    this.affectedByReList = lastResourceVersionBeforeReList != null;
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

    long maxRelatedRv = relatedEvents.lastKey();

    // While an in-flight write hasn't recorded its own RV yet, events past
    // the highest known own RV may still turn out to be that write's echo —
    // restrict the synth window so they're held until either the RV arrives
    // or the write completes. ownRvCount is monotonic across cleanups so
    // already-recorded RVs are not re-classified as "pending" once forgotten.
    Long cutoff;
    if (activeUpdates > ownRvCount) {
      if (ownResourceVersions.isEmpty()) {
        return Optional.empty();
      }
      cutoff = ownResourceVersions.last();
    } else {
      cutoff = maxRelatedRv;
    }

    var windowMap = relatedEvents.headMap(cutoff + 1);
    if (windowMap.isEmpty()) {
      return Optional.empty();
    }

    boolean foundForeign = false;
    for (var entry : windowMap.entrySet()) {
      if (!isOwnEcho(entry.getKey(), entry.getValue())) {
        foundForeign = true;
        break;
      }
    }

    Long prevSeen = lastSeenRelatedRv;
    Optional<GenericResourceEvent> result = Optional.empty();

    // Emit if there is a foreign event in the window, or if a previously emitted
    // event already advanced the reconciler's view and a *new* event (not one we
    // already saw at a prior check) now moves it further. ReList also forces an
    // emit since it may have hidden events while it was running.
    boolean shouldEmit =
        foundForeign
            || (lastEmittedResourceRv != null && (prevSeen == null || cutoff > prevSeen))
            || affectedByReList;

    if (shouldEmit) {
      // Synthesize only from events that are *new* since the last check;
      // carryover events (RV ≤ prevSeen) were already considered before and
      // should not drive the synthesized event's resource versions.
      var synthWindow = prevSeen == null ? windowMap : windowMap.tailMap(prevSeen + 1);

      // When affected by a reList, treat events at or before the reList boundary
      // as captured *during* relist and not informative — only events strictly
      // after the boundary drive the synthesized output.
      var effectiveWindow =
          affectedByReList && lastResourceVersionBeforeReList != null
              ? synthWindow.tailMap(lastResourceVersionBeforeReList + 1)
              : synthWindow;

      if (!effectiveWindow.isEmpty()) {
        var firstEvent = effectiveWindow.get(effectiveWindow.firstKey());
        var lastEvent = effectiveWindow.get(effectiveWindow.lastKey());

        // Identify the last DELETE in the synth window; a DELETE marks the
        // boundary of the "current life" of the resource — anything before it
        // represents a state that no longer exists.
        GenericResourceEvent lastDelete = null;
        boolean hasForeign = false;
        boolean allForeignAreDeletes = true;
        for (var entry : effectiveWindow.entrySet()) {
          var ev = entry.getValue();
          if (ev.getAction() == ResourceAction.DELETED) {
            lastDelete = ev;
          }
          if (!isOwnEcho(entry.getKey(), ev)) {
            hasForeign = true;
            if (ev.getAction() != ResourceAction.DELETED) {
              allForeignAreDeletes = false;
            }
          }
        }
        boolean lastIsOwnEcho = isOwnEcho(effectiveWindow.lastKey(), lastEvent);
        boolean reListBeforeFirstOwn =
            affectedByReList
                && !ownResourceVersions.isEmpty()
                && lastResourceVersionBeforeReList != null
                && lastResourceVersionBeforeReList < ownResourceVersions.first();

        if (affectedByReList && (hasForeign || reListBeforeFirstOwn)) {
          // ReList obscured part of the timeline AND something happened that
          // wasn't purely our own activity — surface a DELETE with
          // lastStateUnknown=true so the reconciler knows the latest known
          // state is uncertain.
          HasMetadata deleted = lastEvent.getResource().orElseThrow();
          result =
              Optional.of(new GenericResourceEvent(ResourceAction.DELETED, deleted, null, true));
          lastEmittedResourceRv = cutoff;
        } else if (!affectedByReList && hasForeign && allForeignAreDeletes && lastIsOwnEcho) {
          // The synth window represents a delete-then-our-recreate sequence:
          // the only foreign activity was DELETE(s) and the resource is back
          // under our control. Nothing for the reconciler to know about.
        } else if (effectiveWindow.size() == 1) {
          result = Optional.of(firstEvent);
          lastEmittedResourceRv = cutoff;
        } else if (lastEvent.getAction() == ResourceAction.DELETED) {
          result = Optional.of(lastEvent);
          lastEmittedResourceRv = cutoff;
        } else if (lastDelete != null) {
          // A DELETE happened in the middle and the resource was recreated/updated
          // afterwards. Synth UPDATED with previous = the deleted state.
          HasMetadata previous = lastDelete.getResource().orElseThrow();
          HasMetadata latest = lastEvent.getResource().orElseThrow();
          result =
              Optional.of(new GenericResourceEvent(ResourceAction.UPDATED, latest, previous, null));
          lastEmittedResourceRv = cutoff;
        } else {
          HasMetadata previous =
              firstEvent
                  .getPreviousResource()
                  .orElseGet(() -> firstEvent.getResource().orElseThrow());
          HasMetadata latest = lastEvent.getResource().orElseThrow();
          result =
              Optional.of(new GenericResourceEvent(ResourceAction.UPDATED, latest, previous, null));
          lastEmittedResourceRv = cutoff;
        }
      }

      if (affectedByReList) {
        affectedByReList = false;
        lastResourceVersionBeforeReList = null;
      }
    }

    lastSeenRelatedRv = prevSeen == null ? maxRelatedRv : Math.max(prevSeen, maxRelatedRv);
    relatedEvents.headMap(cutoff + 1).clear();
    ownResourceVersions.headSet(cutoff + 1).clear();
    return result;
  }

  private boolean isOwnEcho(Long resourceVersion, GenericResourceEvent event) {
    return event.getAction() != ResourceAction.DELETED
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
    ownRvCount++;
  }

  public void addRelatedEvent(GenericResourceEvent event) {
    relatedEvents.put(
        Long.parseLong(event.getResource().orElseThrow().getMetadata().getResourceVersion()),
        event);
  }

  public synchronized void setReListStartedFrom(String lastResourceVersionBeforeReList) {
    this.lastResourceVersionBeforeReList = Long.parseLong(lastResourceVersionBeforeReList);
    this.affectedByReList = true;
  }

  public synchronized void setReListFinished() {
    // Marker: relist has completed and check() may now process. The relist
    // boundary (lastResourceVersionBeforeReList) is consumed by the next check
    // and reset there along with affectedByReList.
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
