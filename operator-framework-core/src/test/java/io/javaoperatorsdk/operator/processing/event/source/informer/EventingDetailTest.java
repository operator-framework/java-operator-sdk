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

class EventingDetailTest {

  // todo delete events
  // todo onBefore on list

  static final Long FIRST_OWN_VERSION = 5L;

  static final ResourceID RESOURCE_ID = new ResourceID("id1", "default");

  EventingDetail eventingDetail = new EventingDetail(null);

  @Test
  void oneOwnVersionNoEvent() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));

    assertThat(eventingDetail.check()).isEmpty();
    assertThat(eventingDetail.canRemoved()).isFalse();
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isFalse();
    assertThat(eventingDetail.getOwnResourceVersions()).containsExactly(FIRST_OWN_VERSION);
  }

  @Test
  void oneOwnVersionEventReceivedEventForIt() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventingDetail.check()).isEmpty();
    assertThat(eventingDetail.canRemoved()).isFalse();

    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
  }

  @Test
  void receivedAsFirstAddEventReturnTheSameEventIfThatIsOnlyRelevant() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(addEvent(FIRST_OWN_VERSION));

    assertThat(eventingDetail.check()).isEmpty();
  }

  @Test
  void oneOwnVersionAdditionalEventReceivedBeforeIt() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION - 1));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    assertThat(eventingDetail.check()).isPresent();
    // check also cleans up the current state, so call is not idempotent
    assertThat(eventingDetail.check()).isEmpty();
    assertThat(eventingDetail.canRemoved()).isFalse();

    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
  }

  @Test
  void twoOwnVersionEventReceivedEventOnlyForFirstThenForSecond() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 1));

    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventingDetail.check()).isEmpty();

    assertThat(eventingDetail.getRelatedEvents()).isEmpty();
    assertThat(eventingDetail.getOwnResourceVersions())
        .containsExactlyInAnyOrder(FIRST_OWN_VERSION + 1);

    eventingDetail.decreaseActiveUpdates();
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isFalse();

    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    assertThat(eventingDetail.check()).isEmpty();
    assertThat(eventingDetail.canRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void twoOwnVersionEventReceivedOne() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 1));

    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventingDetail.check()).isEmpty();

    assertThat(eventingDetail.getRelatedEvents()).isEmpty();
    assertThat(eventingDetail.getOwnResourceVersions())
        .containsExactlyInAnyOrder(FIRST_OWN_VERSION + 1);

    eventingDetail.decreaseActiveUpdates();
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isFalse();
  }

  @Test
  void receivedAddEventAfterOurUpdate() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(addEvent(FIRST_OWN_VERSION + 1));

    assertThat(eventingDetail.check())
        .hasValueSatisfying(e -> assertAddEvent(e, FIRST_OWN_VERSION + 1));

    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.check()).isEmpty();
    assertThat(eventingDetail.canRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void canRemovedIfNoActiveUpdatesOnly() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    assertThat(eventingDetail.check()).isEmpty();
    eventingDetail.decreaseActiveUpdates();

    assertThat(eventingDetail.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION));
  }

  @Test
  void propagateEventIfNoOwnResourceAndNoActiveUpdate() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventingDetail.decreaseActiveUpdates();

    assertThat(eventingDetail.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION));
    assertThat(eventingDetail.canRemoved()).isFalse();
    assertEmptyState();
  }

  @Test
  void receiveEventAfterEventForOwnUpdate() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 1));

    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventingDetail.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 2, FIRST_OWN_VERSION - 1));

    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void doNotIncludeAfterEventForFirstOwnUpdateIfOtherOwnUpdateIsActive() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 1));

    eventingDetail.increaseActiveUpdates();

    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 2));
    // We do not expect the update (+2) to be added here to the first check since
    // other parallel update is going on.
    assertThat(eventingDetail.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 1, FIRST_OWN_VERSION - 1));

    eventingDetail.decreaseActiveUpdates();

    assertThat(eventingDetail.getRelatedEvents()).isNotEmpty();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 2));

    assertThat(eventingDetail.check()).isEmpty();

    eventingDetail.decreaseActiveUpdates();

    assertThat(eventingDetail.canRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void assertMultipleUpdatesAndIntermediateEventBetween() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 2));

    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventingDetail.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 2, FIRST_OWN_VERSION - 1));
    assertThat(eventingDetail.check()).isEmpty();

    eventingDetail.decreaseActiveUpdates();
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void receiveIntermediateBetweenTwoOwnUpdates() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 2));

    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));

    assertThat(eventingDetail.check())
        .hasValueSatisfying(
            e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 1, FIRST_OWN_VERSION - 1));
    assertThat(eventingDetail.canRemoved()).isFalse();

    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isFalse();
    assertThat(eventingDetail.getRelatedEvents()).isEmpty();
    assertThat(eventingDetail.getOwnResourceVersions()).containsExactly(FIRST_OWN_VERSION + 2);

    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 2));
    assertThat(eventingDetail.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 2));

    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
    assertEmptyState();
  }

  @Test
  void deleteEventAsLastEvent_simpleCase() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventingDetail.check()).hasValueSatisfying(this::assertDeleteEvent);
    assertThat(eventingDetail.canRemoved()).isFalse();
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
  }

  @Test
  void deleteEventOnMiddleOfOwnUpdate() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 2));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 1));
    eventingDetail.addRelatedEvent(addEvent(FIRST_OWN_VERSION + 2));

    // it is questionable in this particular case we should propagate last Add or Update event.
    // check also cleans up the current since we received event for our own resource
    assertThat(eventingDetail.check())
        .hasValueSatisfying(e -> assertUpdateEvent(e, FIRST_OWN_VERSION + 2));
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
  }

  @Test
  void deleteEventAsAdditionalEventAfterOwnUpdates() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 1));

    // check also cleans up the current since we received event for our own resource
    assertThat(eventingDetail.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 1));

    assertThat(eventingDetail.canRemoved()).isFalse();
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
  }

  @Test
  void additionalDeleteEvent() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION + 1));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventingDetail.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventingDetail.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 2));
    assertThat(eventingDetail.check()).isEmpty();

    assertEmptyState();
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
  }

  @Test
  void additionalEventAndDeleteEvent() {
    eventingDetail.increaseActiveUpdates();
    eventingDetail.addToOwnResourceVersions(s(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION));
    eventingDetail.addRelatedEvent(updateEvent(FIRST_OWN_VERSION + 1));
    eventingDetail.addRelatedEvent(deleteEvent(FIRST_OWN_VERSION + 2));

    assertThat(eventingDetail.check())
        .hasValueSatisfying(e -> assertDeleteEvent(e, FIRST_OWN_VERSION + 2));
    assertThat(eventingDetail.check()).isEmpty();

    assertEmptyState();
    eventingDetail.decreaseActiveUpdates();
    assertThat(eventingDetail.canRemoved()).isTrue();
  }

  // this is very similar to reList since unknown state only happens during reList
  @Test
  void deleteEventWithUnknownState() {}

  @Test
  void reListBeforeUpdateStarted() {}

  @Test
  void reListInMiddleOfUpdate() {}

  @Test
  void reListAfterAllUpdatesReceived() {}

  void assertUpdateEvent(GenericResourceEvent event) {
    assertUpdateEvent(event, FIRST_OWN_VERSION);
  }

  void assertUpdateEvent(GenericResourceEvent event, Long resourceVersion) {
    assertUpdateEvent(event, resourceVersion, resourceVersion - 1);
  }

  void assertUpdateEvent(
      GenericResourceEvent event, Long resourceVersion, Long previousResourceVersion) {
    assertThat(event.getAction()).isEqualTo(UPDATED);
    assertThat(event.getResource().orElseThrow().getMetadata().getResourceVersion())
        .isEqualTo(s(resourceVersion));
    assertThat(event.getPreviousResource().orElseThrow().getMetadata().getResourceVersion())
        .isEqualTo(s(previousResourceVersion));
    assertThat(event.getLastStateUnknow()).isNull();
  }

  void assertAddEvent(GenericResourceEvent event) {
    assertAddEvent(event, FIRST_OWN_VERSION);
  }

  void assertAddEvent(GenericResourceEvent event, Long resourceVersion) {
    assertThat(event.getAction()).isEqualTo(ADDED);
    assertThat(event.getResource().orElseThrow().getMetadata().getResourceVersion())
        .isEqualTo(s(resourceVersion));
    assertThat(event.getPreviousResource()).isEmpty();
    assertThat(event.getLastStateUnknow()).isNull();
  }

  void assertDeleteEvent(GenericResourceEvent event) {
    assertDeleteEvent(event, FIRST_OWN_VERSION, true);
  }

  void assertDeleteEvent(GenericResourceEvent event, Long resourceVersion) {
    assertDeleteEvent(event, resourceVersion, true);
  }

  void assertDeleteEvent(
      GenericResourceEvent event, Long resourceVersion, boolean lastStateUnknown) {
    assertThat(event.getAction()).isEqualTo(DELETED);
    assertThat(event.getResource().orElseThrow().getMetadata().getResourceVersion())
        .isEqualTo(s(resourceVersion));
    assertThat(event.getPreviousResource()).isEmpty();
    assertThat(event.getLastStateUnknow()).isEqualTo(lastStateUnknown);
  }

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

  GenericResourceEvent deleteEventUnknownLastState(long version) {
    return new GenericResourceEvent(DELETED, testResource(version), null, null);
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
    assertThat(eventingDetail.getRelatedEvents()).isEmpty();
    assertThat(eventingDetail.getOwnResourceVersions()).isEmpty();
  }

  private String s(long l) {
    return Long.toString(l);
  }
}
