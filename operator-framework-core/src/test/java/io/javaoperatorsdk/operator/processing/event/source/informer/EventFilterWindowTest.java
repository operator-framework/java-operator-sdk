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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static io.javaoperatorsdk.operator.processing.event.source.ResourceAction.ADDED;
import static io.javaoperatorsdk.operator.processing.event.source.ResourceAction.DELETED;
import static io.javaoperatorsdk.operator.processing.event.source.ResourceAction.UPDATED;
import static org.assertj.core.api.Assertions.assertThat;

class EventFilterWindowTest {

  static final Long FIRST_OWN_VERSION = 5L;

  static final ResourceID RESOURCE_ID = new ResourceID("id1", "default");

  EventFilterWindow eventFilterWindow = new EventFilterWindow(false);

  @Test
  void oneOwnVersionNoEvent() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));

    assertThat(eventFilterWindow.check()).isEmpty();
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();
    assertThat(eventFilterWindow.getOwnResourceVersions()).containsExactly(FIRST_OWN_VERSION);
  }

  @Test
  void oneOwnVersionEventReceivedEventForIt() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventFilterWindow.check()).isEmpty();
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();

    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void receivedAsFirstAddEventReturnTheSameEventIfThatIsOnlyRelevant() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(addEvent(FIRST_OWN_VERSION));

    assertThat(eventFilterWindow.check()).isEmpty();
  }

  @Test
  void oneOwnVersionAdditionalEventReceivedBeforeIt() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION - 1));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    assertThat(eventFilterWindow.check()).isPresent();
    // check also cleans up the current state, so call is not idempotent
    assertThat(eventFilterWindow.check()).isEmpty();
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();

    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void twoOwnVersionEventReceivedEventOnlyForFirstThenForSecond() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 1));

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventFilterWindow.check()).isEmpty();

    assertThat(eventFilterWindow.getRelatedEvents()).isEmpty();
    assertThat(eventFilterWindow.getOwnResourceVersions())
        .containsExactlyInAnyOrder(FIRST_OWN_VERSION + 1);

    eventFilterWindow.decreaseActiveUpdates();
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    assertThat(eventFilterWindow.check()).isEmpty();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void twoOwnVersionEventReceivedOne() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 1));

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventFilterWindow.check()).isEmpty();

    assertThat(eventFilterWindow.getRelatedEvents()).isEmpty();
    assertThat(eventFilterWindow.getOwnResourceVersions())
        .containsExactlyInAnyOrder(FIRST_OWN_VERSION + 1);

    eventFilterWindow.decreaseActiveUpdates();
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();
  }

  @Test
  void receivedAddEventAfterOurUpdate() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(addEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertAddEvent(e, FIRST_OWN_VERSION + 1));

    assertThat(eventFilterWindow.check()).isEmpty();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void receivedAddEventAfterOurUpdateDone() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.decreaseActiveUpdates();
    eventFilterWindow.addRelatedEvent(addEvent(FIRST_OWN_VERSION + 1));
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertAddEvent(e, FIRST_OWN_VERSION + 1));

    assertThat(eventFilterWindow.check()).isEmpty();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void canBeRemovedIfNoActiveUpdatesOnly() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    assertThat(eventFilterWindow.check()).isEmpty();
    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION));
  }

  @Test
  void propagateEventIfNoOwnResourceAndNoActiveUpdate() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION));
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void receiveEventAfterEventForOwnUpdate() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 1));

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 2));

    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 2, FIRST_OWN_VERSION - 1));

    assertThat(eventFilterWindow.check()).isEmpty();

    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void doNotIncludeAfterEventForFirstOwnUpdateIfOtherOwnUpdateIsActive() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 1));

    eventFilterWindow.increaseActiveUpdates();

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 2));
    // We do not expect the update (+2) to be added here to the first check since
    // other parallel update is going on.
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 1, FIRST_OWN_VERSION - 1));

    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.getRelatedEvents()).isNotEmpty();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 2));

    assertThat(eventFilterWindow.check()).isEmpty();

    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void assertMultipleUpdatesAndIntermediateEventBetween() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 2));

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 2, FIRST_OWN_VERSION - 1));
    assertThat(eventFilterWindow.check()).isEmpty();

    eventFilterWindow.decreaseActiveUpdates();
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void receiveIntermediateBetweenTwoOwnUpdates() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 2));

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 1, FIRST_OWN_VERSION - 1));
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();

    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();
    assertThat(eventFilterWindow.getRelatedEvents()).isEmpty();
    assertThat(eventFilterWindow.getOwnResourceVersions()).containsExactly(FIRST_OWN_VERSION + 2);

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 2));
    assertThat(eventFilterWindow.check()).isEmpty();

    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void deleteEventAsLastEvent_simpleCase() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION));
    assertThat(eventFilterWindow.check()).hasValueSatisfying(this::assertDeleteEvent);
    assertThat(eventFilterWindow.canBeRemoved()).isFalse();

    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void deleteEventBeforeOurUpdate() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION - 1));
    eventFilterWindow.addRelatedEvent(addEvent(FIRST_OWN_VERSION));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventFilterWindow.check()).isEmpty();

    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void deleteEventOnMiddleOfOwnUpdate() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 2));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.addRelatedEvent(addEvent(FIRST_OWN_VERSION + 2));

    // it is questionable in this particular case we should propagate last Add or Update event.
    // check also cleans up the current since we received event for our own resource
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 2, FIRST_OWN_VERSION - 1));
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void deleteEventAsAdditionalEventAfterOwnUpdates() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 1));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventFilterWindow.check()).isEmpty();

    assertThat(eventFilterWindow.canBeRemoved()).isFalse();
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 1));
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void additionalDeleteEvent() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 1));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 1, FIRST_OWN_VERSION - 1));

    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.canBeRemoved()).isFalse();
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 2));

    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void additionalEventAndDeleteEvent() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventFilterWindow.check()).isEmpty();

    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 2));
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  @Disabled("should be part of event filter support")
  void additionalEventAndDeleteEventNoUpdate() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 2));
    assertThat(eventFilterWindow.check()).isEmpty();

    assertEmptyState();
    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void deleteEventInMiddleTwoUpdates() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    assertThat(eventFilterWindow.check()).isEmpty();

    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 1));

    eventFilterWindow
        .increaseActiveUpdates(); // started new update delete event should not be included in first

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 1));
    assertEmptyState();

    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 2));
    eventFilterWindow.addRelatedEvent(addEvent(FIRST_OWN_VERSION + 2));
    // delete event should be skipped in these cases and taking directly the last event
    assertThat(eventFilterWindow.check()).isEmpty();

    eventFilterWindow.decreaseActiveUpdates();

    assertEmptyState();
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void deleteEventAfterTwoUpdates() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));

    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 1));

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));

    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventFilterWindow.check()).isEmpty();

    eventFilterWindow.decreaseActiveUpdates();
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 2));

    assertEmptyState();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void deleteEventAfterTwoUpdatesFinished() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));

    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 1));

    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));

    eventFilterWindow.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 2));

    eventFilterWindow.decreaseActiveUpdates();
    eventFilterWindow.decreaseActiveUpdates();
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 2));

    assertEmptyState();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  //  if there is a re-list other events / changes might have arrived before re-list was done,
  //  so we always assume that there was an additional event there
  @Test
  void reListBeforeUpdateStarted() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.setReListStarted();
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.setReListFinished();

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION));

    eventFilterWindow.decreaseActiveUpdates();
    assertEmptyState();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void reListHappensAfterUpdate() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventFilterWindow.setReListStarted();
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.setReListFinished();

    assertThat(eventFilterWindow.check()).isEmpty();
    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 1));
    assertEmptyState();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  void reListBetweenTwoUpdates() {
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION + 1));
    eventFilterWindow.setReListStarted();
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventFilterWindow.setReListFinished();

    // this should be the case regardless of re-list
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 1, FIRST_OWN_VERSION - 1));

    eventFilterWindow.decreaseActiveUpdates();
    eventFilterWindow.decreaseActiveUpdates();
    assertEmptyState();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  @Test
  @Disabled("EventFilterWindow drops coalesced foreign spec changes — #3455")
  void coalescedForeignSpecChangeShouldNotBeFilteredByOwnWriteRV() {
    // Reproduces the scenario where a controller's SSA patch merges with a concurrent
    // foreign spec change. The informer coalesces both events into one at the own-write RV.
    //
    // Real-world sequence (from Kroxylicious GHA failure):
    //   1. Controller reconciles resource at gen=1
    //   2. Foreign actor changes spec → gen=2 → new RV on API server
    //   3. Controller's SSA patchResource merges on top → returns RV=5 (gen=2)
    //   4. Informer coalesces both events (same resource key) into one at RV=5
    //   5. EventFilterWindow sees relatedEvents={5} == ownResourceVersions={5} → drops
    //   6. Controller never learns about gen=2 → stuck until maxReconciliationInterval
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));

    var resource = testResource(FIRST_OWN_VERSION);
    resource.getMetadata().setGeneration(2L);
    var previousResource = testResource(FIRST_OWN_VERSION - 1);
    previousResource.getMetadata().setGeneration(1L);
    eventFilterWindow.addRelatedEvent(
        new ExtendedResourceEvent(UPDATED, resource, previousResource, null));

    eventFilterWindow.decreaseActiveUpdates();

    assertThat(eventFilterWindow.check())
        .as(
            "Event at own-write RV with generation change (1→2) should propagate:"
                + " a foreign spec change was coalesced with the own-write echo")
        .isPresent();
  }

  @Test
  void combinedCaseWithEarlyEvent() {
    // Scenario: an own write is in flight (RV recorded), a foreign event with a
    // lower RV arrives, then the write completes (active → 0) but no echo for
    // our own RV ever arrives. The held foreign event must surface — otherwise
    // the window wedges (canRemoved stays false because relatedEvents is not
    // empty) and the reconciler never learns about the foreign change.
    eventFilterWindow.increaseActiveUpdates();
    eventFilterWindow.addToOwnUpdateVersions(s(FIRST_OWN_VERSION));
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION - 2));

    // Held while the write is in flight.
    assertThat(eventFilterWindow.check()).isEmpty();

    // Write completes, no echo for own=[FIRST_OWN_VERSION] ever arrived.
    eventFilterWindow.decreaseActiveUpdates();

    eventFilterWindow.setReListStarted();
    eventFilterWindow.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    // The foreign event must surface now.
    eventFilterWindow.setReListFinished();
    assertThat(eventFilterWindow.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION, FIRST_OWN_VERSION - 3));

    assertEmptyState();
    assertThat(eventFilterWindow.canBeRemoved()).isTrue();
  }

  void assertUpdateEvent(ExtendedResourceEvent event, Long resourceVersion) {
    assertUpdateEvent(event, resourceVersion, resourceVersion - 1);
  }

  void assertUpdateEvent(
      ExtendedResourceEvent event, Long resourceVersion, Long previousResourceVersion) {
    assertThat(event.getAction()).isEqualTo(UPDATED);
    assertThat(event.getResource().orElseThrow().getMetadata().getResourceVersion())
        .isEqualTo(s(resourceVersion));
    assertThat(event.getPreviousResource().orElseThrow().getMetadata().getResourceVersion())
        .isEqualTo(s(previousResourceVersion));
    assertThat(event.isLastStateUnknown()).isNull();
  }

  void assertAddEvent(ExtendedResourceEvent event, Long resourceVersion) {
    assertThat(event.getAction()).isEqualTo(ADDED);
    assertThat(event.getResource().orElseThrow().getMetadata().getResourceVersion())
        .isEqualTo(s(resourceVersion));
    assertThat(event.getPreviousResource()).isEmpty();
    assertThat(event.isLastStateUnknown()).isNull();
  }

  void assertDeleteEvent(ExtendedResourceEvent event) {
    assertDeleteEvent(event, FIRST_OWN_VERSION);
  }

  void assertDeleteEvent(ExtendedResourceEvent event, Long resourceVersion) {
    assertThat(event.getAction()).isEqualTo(DELETED);
    assertThat(event.getResource().orElseThrow().getMetadata().getResourceVersion())
        .isEqualTo(s(resourceVersion));
    assertThat(event.getPreviousResource()).isEmpty();
    assertThat(event.isLastStateUnknown()).isTrue();
  }

  ExtendedResourceEvent updateEvent(long version) {
    return new ExtendedResourceEvent(
        UPDATED, testResource(version), testResource(version - 1), null);
  }

  ExtendedResourceEvent addEvent(long version) {
    return new ExtendedResourceEvent(ADDED, testResource(version), null, null);
  }

  ExtendedResourceEvent deleteEvent(long version) {
    return new ExtendedResourceEvent(DELETED, testResource(version), null, true);
  }

  ConfigMap testResource(Long version) {
    var cm = new ConfigMap();
    cm.setMetadata(
        new ObjectMetaBuilder()
            .withName(RESOURCE_ID.getName())
            .withNamespace(RESOURCE_ID.getNamespace().orElseThrow())
            .withResourceVersion(version.toString())
            .build());
    return cm;
  }

  private void assertEmptyState() {
    assertThat(eventFilterWindow.getRelatedEvents()).isEmpty();
    assertThat(eventFilterWindow.getOwnResourceVersions()).isEmpty();
  }

  private String s(long l) {
    return Long.toString(l);
  }
}
