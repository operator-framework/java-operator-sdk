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

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static io.javaoperatorsdk.operator.processing.event.source.ResourceAction.ADDED;
import static io.javaoperatorsdk.operator.processing.event.source.ResourceAction.DELETED;
import static io.javaoperatorsdk.operator.processing.event.source.ResourceAction.UPDATED;
import static org.assertj.core.api.Assertions.assertThat;

class EventFilterSupportTest {

  static final Long FIRST_OWN_VERSION = 5L;
  static final ResourceID RESOURCE_ID = new ResourceID("id1", "default");
  static final ResourceID OTHER_RESOURCE_ID = new ResourceID("id2", "default");

  EventFilterSupport support = new EventFilterSupport();

  @Test
  void startEventFilteringCreatesEventingDetail() {
    support.startEventFilteringModify(RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();
    assertThat(support.getEventFilterWindows()).containsOnlyKeys(RESOURCE_ID);
  }

  @Test
  void startEventFilteringTwiceReusesEventingDetail() {
    support.startEventFilteringModify(RESOURCE_ID);
    var first = support.getEventFilterWindows().get(RESOURCE_ID);

    support.startEventFilteringModify(RESOURCE_ID);
    var second = support.getEventFilterWindows().get(RESOURCE_ID);

    assertThat(second).isSameAs(first);
  }

  @Test
  void doneEventFilterModifyEmptyWhenNoEventingDetail() {
    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
  }

  @Test
  void doneEventFilterModifyRemovesDetailWhenRemovable() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();

    var res = support.doneEventFilterModify(RESOURCE_ID);

    assertThat(res).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void scenarioWithSurroundingEvent() {
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION - 1)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));

    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1))).isEmpty();

    var res = support.doneEventFilterModify(RESOURCE_ID);

    assertThat(res).hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 2)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
  }

  @Test
  void processEventPropagatesWhenNoEventingDetail() {
    var event = updateEvent(FIRST_OWN_VERSION);

    var res = support.processEvent(RESOURCE_ID, event);

    assertThat(res).contains(event);
  }

  @Test
  void processEventHoldsOwnEcho() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    var res = support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION));

    assertThat(res).isEmpty();
  }

  @Test
  void processEventEmitsSynthForForeignEvent() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION - 1));

    var res = support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION));

    assertThat(res).hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
  }

  @Test
  void processEventEmitsAddedForeignVerbatim() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    var addEvent = addEvent(FIRST_OWN_VERSION);
    var updateEvent = addEvent(FIRST_OWN_VERSION + 1);
    support.processEvent(RESOURCE_ID, addEvent);

    var res = support.processEvent(RESOURCE_ID, updateEvent);
    assertThat(res).isEmpty();

    res = support.doneEventFilterModify(RESOURCE_ID);

    assertThat(res).contains(addEvent);
  }

  @Test
  void addToOwnResourceVersionsIsNoOpWithoutEventingDetail() {
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void handleGhostResourceRemovalDropsWindow() {
    support.startEventFilteringModify(RESOURCE_ID);

    support.handleGhostResourceRemoval(RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void handleGhostResourceRemovalIsNoOpForUnknownResource() {
    support.startEventFilteringModify(RESOURCE_ID);

    support.handleGhostResourceRemoval(OTHER_RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();
    assertThat(support.isActiveUpdateFor(OTHER_RESOURCE_ID)).isFalse();
  }

  @Test
  void fullLifecycleOwnWriteOnlyEmitsNothingAndCleansUp() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();

    var res = support.doneEventFilterModify(RESOURCE_ID);

    assertThat(res).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void fullLifecycleForeignBeforeOwnEchoEmitsSynth() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    var foreign = updateEvent(FIRST_OWN_VERSION - 1);
    assertThat(support.processEvent(RESOURCE_ID, foreign)).isEmpty();

    // catch-up emit triggered by the own echo arriving after the prior emit
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isPresent();
    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void oneOwnVersionNoEvent() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    // own RV recorded but no echo arrived yet → window stays
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();
    assertThat(support.getEventFilterWindows().get(RESOURCE_ID).getOwnResourceVersions())
        .containsExactly(FIRST_OWN_VERSION);
  }

  @Test
  void oneOwnVersionEventReceivedEventForIt() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void receivedAsFirstAddEventReturnTheSameEventIfThatIsOnlyRelevant() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.processEvent(RESOURCE_ID, addEvent(FIRST_OWN_VERSION))).isEmpty();
  }

  @Test
  void oneOwnVersionAdditionalEventReceivedBeforeIt() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION - 1))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isPresent();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void twoOwnVersionEventReceivedEventOnlyForFirstThenForSecond() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 1));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    var window = support.getEventFilterWindows().get(RESOURCE_ID);
    assertThat(window.getRelatedEvents()).isEmpty();
    assertThat(window.getOwnResourceVersions()).containsExactly(FIRST_OWN_VERSION + 1);

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1))).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void twoOwnVersionEventReceivedOne() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 1));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();

    var window = support.getEventFilterWindows().get(RESOURCE_ID);
    assertThat(window.getRelatedEvents()).isEmpty();
    assertThat(window.getOwnResourceVersions()).containsExactly(FIRST_OWN_VERSION + 1);

    support.doneEventFilterModify(RESOURCE_ID);
    support.doneEventFilterModify(RESOURCE_ID);
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();
  }

  @Test
  void receivedAddEventAfterOurUpdate() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.processEvent(RESOURCE_ID, addEvent(FIRST_OWN_VERSION + 1))).isEmpty();
    assertThat(support.doneEventFilterModify(RESOURCE_ID))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(ADDED));
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void receivedAddEventAfterOurUpdateDone() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();

    // Window remains because own=[5] is non-empty. Late ADDED arrives after done.
    assertThat(support.processEvent(RESOURCE_ID, addEvent(FIRST_OWN_VERSION + 1)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(ADDED));
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void canBeRemovedIfNoActiveUpdatesOnly() {
    support.startEventFilteringModify(RESOURCE_ID);
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();

    assertThat(support.doneEventFilterModify(RESOURCE_ID))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
  }

  @Test
  void propagateEventIfNoOwnResourceAndNoActiveUpdate() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.doneEventFilterModify(RESOURCE_ID);
    // After the done call, active=0 and own is empty → window removed.
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();

    // A subsequent event has no window → propagated verbatim.
    var event = updateEvent(FIRST_OWN_VERSION);
    assertThat(support.processEvent(RESOURCE_ID, event)).contains(event);
  }

  @Test
  void receiveEventAfterEventForOwnUpdate() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 1));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 2))).isEmpty();

    assertThat(support.doneEventFilterModify(RESOURCE_ID))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void doNotIncludeAfterEventForFirstOwnUpdateIfOtherOwnUpdateIsActive() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 1));
    support.startEventFilteringModify(RESOURCE_ID);

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1))).isPresent();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 2))).isEmpty();

    support.doneEventFilterModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 2));
    support.doneEventFilterModify(RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void assertMultipleUpdatesAndIntermediateEventBetween() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 2));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 2)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));

    support.doneEventFilterModify(RESOURCE_ID);
    support.doneEventFilterModify(RESOURCE_ID);
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void receiveIntermediateBetweenTwoOwnUpdates() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 2));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1))).isEmpty();

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 2)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void deleteEventAsLastEvent_simpleCase() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.processEvent(RESOURCE_ID, deleteEvent(FIRST_OWN_VERSION)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(DELETED));
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void deleteEventBeforeOurUpdate() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.processEvent(RESOURCE_ID, deleteEvent(FIRST_OWN_VERSION - 1))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, addEvent(FIRST_OWN_VERSION))).isEmpty();

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void deleteEventOnMiddleOfOwnUpdate() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 2));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, deleteEvent(FIRST_OWN_VERSION + 1))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, addEvent(FIRST_OWN_VERSION + 2)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void deleteEventAsAdditionalEventAfterOwnUpdates() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, deleteEvent(FIRST_OWN_VERSION + 1)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(DELETED));

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void additionalDeleteEvent() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 1));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
    assertThat(support.processEvent(RESOURCE_ID, deleteEvent(FIRST_OWN_VERSION + 2)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(DELETED));

    assertThat(support.processEvent(RESOURCE_ID, addEvent(FIRST_OWN_VERSION + 3)));
    assertThat(support.doneEventFilterModify(RESOURCE_ID))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(ADDED));

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void deleteEventInMiddleTwoUpdates() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, deleteEvent(FIRST_OWN_VERSION + 1)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(DELETED));

    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 2));
    assertThat(support.processEvent(RESOURCE_ID, addEvent(FIRST_OWN_VERSION + 2))).isEmpty();

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void deleteEventAfterTwoUpdates() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 1));

    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, deleteEvent(FIRST_OWN_VERSION + 2)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(DELETED));

    support.doneEventFilterModify(RESOURCE_ID);
    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void reListBeforeUpdateStarted() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.setStartingReList();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
    support.setRelistFinished();

    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void reListHappensAfterUpdate() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();
    support.setStartingReList();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1))).isEmpty();
    support.setRelistFinished();

    assertThat(support.doneEventFilterModify(RESOURCE_ID))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void reListBetweenTwoUpdates() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();

    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION + 1));
    support.setStartingReList();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION + 1)))
        .hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
    support.setRelistFinished();

    support.doneEventFilterModify(RESOURCE_ID);
    support.doneEventFilterModify(RESOURCE_ID);
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  // -------- ghost resource removal in combination with active windows / events --------

  @Test
  void ghostRemovalDuringActiveUpdateClearsWindow() {
    // A ghost cleanup arriving while an own write is in flight wipes the window
    // outright (current semantics — see EventFilterSupport.handleGhostResourceRemoval).
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION - 1));
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isTrue();

    support.handleGhostResourceRemoval(RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
    // Subsequent events for this resource have no window → propagate verbatim.
    var follow = updateEvent(FIRST_OWN_VERSION + 5);
    assertThat(support.processEvent(RESOURCE_ID, follow)).contains(follow);
  }

  @Test
  void ghostRemovalAfterEventsHaveBeenHeldDropsThem() {
    // Held foreign events that haven't yet been emitted are discarded by ghost removal.
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION - 2))).isEmpty();
    assertThat(support.processEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION - 1))).isEmpty();
    var window = support.getEventFilterWindows().get(RESOURCE_ID);
    assertThat(window.getRelatedEvents()).isNotEmpty();

    support.handleGhostResourceRemoval(RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void ghostRemovalDuringReListAffectsOnlyTargetResource() {
    // Ghost removal targeting one resource doesn't disturb a parallel reList window
    // for another resource.
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.startEventFilteringModify(OTHER_RESOURCE_ID);
    support.setStartingReList();

    support.handleGhostResourceRemoval(RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
    assertThat(support.isActiveUpdateFor(OTHER_RESOURCE_ID)).isTrue();
  }

  // -------- end of replicated tests --------

  GenericResourceEvent updateEvent(long version) {
    return new GenericResourceEvent(
        UPDATED, testResource(version), testResource(version - 1), null);
  }

  GenericResourceEvent addEvent(long version) {
    return new GenericResourceEvent(ADDED, testResource(version), null, null);
  }

  GenericResourceEvent deleteEvent(long version) {
    return new GenericResourceEvent(DELETED, testResource(version), null, true);
  }

  ConfigMap testResource(long version) {
    var cm = new ConfigMap();
    cm.setMetadata(
        new ObjectMetaBuilder()
            .withName(RESOURCE_ID.getName())
            .withNamespace(RESOURCE_ID.getNamespace().orElseThrow())
            .withResourceVersion(Long.toString(version))
            .build());
    return cm;
  }

  private String s(long l) {
    return Long.toString(l);
  }
}
