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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

class EventFilterDetails {

  private int activeUpdates = 0;
  private final List<GenericResourceEvent> relatedEvents = new ArrayList<>(5);
  private final Set<String> allOwnResourceVersions = new HashSet<>(5);
  private boolean affectedByReList;
  private volatile boolean reListSummaryEventSent = false;

  public EventFilterDetails(boolean affectedByReList) {
    this.affectedByReList = affectedByReList;
  }

  public void increaseActiveUpdates() {
    activeUpdates = activeUpdates + 1;
  }

  /**
   * resourceVersion is needed for case when multiple parallel updates happening inside the
   * controller to prevent race condition and send event from {@link
   * ManagedInformerEventSource#eventFilteringUpdateAndCacheResource(HasMetadata, UnaryOperator)}
   */
  public boolean decreaseActiveUpdates() {
    activeUpdates = activeUpdates - 1;
    return activeUpdates == 0;
  }

  public int getActiveUpdates() {
    return activeUpdates;
  }

  public boolean isNoActiveUpdate() {
    return activeUpdates == 0;
  }

  void addToOwnResourceVersions(String updateVersion) {
    allOwnResourceVersions.add(updateVersion);
  }

  public void addRelatedEvent(GenericResourceEvent event) {
    relatedEvents.add(event);
  }

  public Optional<GenericResourceEvent> summaryEventForReList() {
    if (!affectedByReList) {
      throw new IllegalStateException(
          "ReList summary event requested to detail not affected by relist");
    }
    if (reListSummaryEventSent) {
      throw new IllegalStateException("ReList summary event already sent");
    }
    reListSummaryEventSent = true;
    if (relatedEvents.isEmpty()) {
      return Optional.empty();
    }
    return summaryEvent();
  }

  // todo unit tests for corner cases with empty collections
  public Optional<GenericResourceEvent> summaryEvent() {
    if (relatedEvents.isEmpty()) {
      return Optional.empty();
    }
    if (allOwnResourceVersions.containsAll(relatedEventResourceVersions())) {
      return Optional.empty();
    }
    return summaryEventInternal();
  }

  private Optional<GenericResourceEvent> summaryEventInternal() {
      // we propagate delete event only if it is the last, if there are newer events
      // means the resource was re-created (not necessarily by our controller)
      var lastEvent = relatedEvents.get(relatedEvents.size() - 1);
      if (lastEvent.getAction() == ResourceAction.DELETED) {
          return Optional.of(lastEvent);
      }
    if (relatedEvents.size() == 1) {
      return Optional.of(relatedEvents.get(0));
    }
    var firstEvent = relatedEvents.get(0);
    var firstResource =
        firstEvent.getPreviousResource().orElseGet(() -> firstEvent.getResource().orElseThrow());

    return Optional.of(
        new GenericResourceEvent(
            ResourceAction.UPDATED,
            relatedEvents.get(relatedEvents.size() - 1).getResource().orElseThrow(),
            firstResource,
            null));
  }

  private Set<String> relatedEventResourceVersions() {
    return relatedEvents.stream()
        .map(e -> e.getResource().orElseThrow().getMetadata().getResourceVersion())
        .collect(Collectors.toSet());
  }

  public boolean newerOrEqualEventReceivedForOwnLastUpdate() {
    // this means our update was not successful
    if (allOwnResourceVersions.isEmpty()) {
      return true;
    }
    String lastOwn =
        allOwnResourceVersions.stream()
            .reduce((a, b) -> ReconcilerUtilsInternal.compareResourceVersions(a, b) >= 0 ? a : b)
            .orElseThrow();
    return relatedEvents.stream()
        .map(e -> e.getResource().orElseThrow().getMetadata().getResourceVersion())
        .anyMatch(rv -> ReconcilerUtilsInternal.compareResourceVersions(rv, lastOwn) >= 0);
  }

  public boolean isAffectedByReList() {
    return affectedByReList;
  }

  public void affectedByReList() {
    this.affectedByReList = true;
  }

  public boolean isReListSummaryEventSent() {
    return reListSummaryEventSent;
  }
}
