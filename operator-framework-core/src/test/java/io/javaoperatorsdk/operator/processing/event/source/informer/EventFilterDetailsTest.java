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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class EventFilterDetailsTest {

  private EventFilterDetails details;

  @BeforeEach
  void setup() {
    details = new EventFilterDetails(false);
  }

  @Test
  void activeUpdatesCounter() {
    assertThat(details.isNoActiveUpdate()).isTrue();
    assertThat(details.getActiveUpdates()).isZero();

    details.increaseActiveUpdates();
    details.increaseActiveUpdates();
    assertThat(details.getActiveUpdates()).isEqualTo(2);
    assertThat(details.isNoActiveUpdate()).isFalse();

    assertThat(details.decreaseActiveUpdates()).isFalse();
    assertThat(details.getActiveUpdates()).isEqualTo(1);

    assertThat(details.decreaseActiveUpdates()).isTrue();
    assertThat(details.isNoActiveUpdate()).isTrue();
  }

  @Test
  void summaryEmptyWhenAllRelatedEventsAreOwn() {
    details.addToOwnResourceVersions("2");
    details.addToOwnResourceVersions("3");
    details.addRelatedEvent(updatedEvent("2", null));
    details.addRelatedEvent(updatedEvent("3", "2"));

    assertThat(details.summaryEvent()).isEmpty();
  }

  @Test
  void summaryReturnsSingleNonOwnEvent() {
    var thirdParty = updatedEvent("4", "3");
    details.addToOwnResourceVersions("2");
    details.addRelatedEvent(thirdParty);

    var summary = details.summaryEvent();

    assertThat(summary).contains(thirdParty);
  }

  @Test
  void summaryReturnsLastEventWhenItIsDelete() {
    var firstUpdate = updatedEvent("3", "2");
    var deleteAtEnd = deleteEvent("4");
    details.addRelatedEvent(firstUpdate);
    details.addRelatedEvent(deleteAtEnd);

    var summary = details.summaryEvent();

    assertThat(summary).contains(deleteAtEnd);
  }

  @Test
  void summaryDoesNotReturnDeleteWhenItIsNotLast() {
    // simulates a delete-then-recreate sequence inside the filter window:
    // returning the DELETE would mask the fact that the resource exists again.
    var deleteEvent = deleteEvent("3");
    var recreate = addedEvent("4");
    details.addRelatedEvent(deleteEvent);
    details.addRelatedEvent(recreate);

    var summary = details.summaryEvent();

    assertThat(summary).isPresent();
    assertThat(summary.get().getAction()).isEqualTo(ResourceAction.UPDATED);
    assertThat(summary.get().getResource().orElseThrow()).isEqualTo(recreate.getResource().get());
  }

  @Test
  void summarySynthesizesUpdatedFromFirstPreviousToLastResource() {
    var first = updatedEvent("3", "2");
    var middle = updatedEvent("4", "3");
    var last = updatedEvent("5", "4");
    details.addRelatedEvent(first);
    details.addRelatedEvent(middle);
    details.addRelatedEvent(last);

    var summary = details.summaryEvent().orElseThrow();

    assertThat(summary.getAction()).isEqualTo(ResourceAction.UPDATED);
    assertThat(summary.getResource().orElseThrow()).isEqualTo(last.getResource().get());
    assertThat(summary.getPreviousResource().orElseThrow())
        .isEqualTo(first.getPreviousResource().get());
    assertThat(summary.getLastStateUnknow()).isNull();
  }

  @Test
  void summaryUsesFirstResourceAsPreviousWhenFirstEventHasNoPrevious() {
    // first event is ADD (no previous resource); synthesis must fall back to the resource itself.
    var added = addedEvent("3");
    var updated = updatedEvent("4", "3");
    details.addRelatedEvent(added);
    details.addRelatedEvent(updated);

    var summary = details.summaryEvent().orElseThrow();

    assertThat(summary.getAction()).isEqualTo(ResourceAction.UPDATED);
    assertThat(summary.getResource().orElseThrow()).isEqualTo(updated.getResource().get());
    assertThat(summary.getPreviousResource().orElseThrow()).isEqualTo(added.getResource().get());
  }

  @Test
  void summarySkipsOwnFilterWhenAtLeastOneEventIsForeign() {
    // even with own rvs in the mix, presence of a non-own event must surface a summary.
    details.addToOwnResourceVersions("3");
    var ownEvent = updatedEvent("3", "2");
    var foreign = updatedEvent("4", "3");
    details.addRelatedEvent(ownEvent);
    details.addRelatedEvent(foreign);

    var summary = details.summaryEvent().orElseThrow();

    assertThat(summary.getAction()).isEqualTo(ResourceAction.UPDATED);
    assertThat(summary.getResource().orElseThrow()).isEqualTo(foreign.getResource().get());
    assertThat(summary.getPreviousResource().orElseThrow())
        .isEqualTo(ownEvent.getPreviousResource().get());
  }

  @Test
  void newerOrEqualReturnsTrueWhenNoOwnVersions() {
    assertThat(details.newerOrEqualEventReceivedForOwnLastUpdate()).isTrue();
    details.addRelatedEvent(updatedEvent("2", null));
    assertThat(details.newerOrEqualEventReceivedForOwnLastUpdate()).isTrue();
  }

  @Test
  void newerOrEqualReturnsFalseWhenNoRelatedEventsYet() {
    details.addToOwnResourceVersions("3");

    assertThat(details.newerOrEqualEventReceivedForOwnLastUpdate()).isFalse();
  }

  @Test
  void newerOrEqualReturnsFalseWhenAllRelatedAreOlderThanLastOwn() {
    details.addToOwnResourceVersions("5");
    details.addRelatedEvent(updatedEvent("3", "2"));
    details.addRelatedEvent(updatedEvent("4", "3"));

    assertThat(details.newerOrEqualEventReceivedForOwnLastUpdate()).isFalse();
  }

  @Test
  void newerOrEqualReturnsTrueWhenRelatedMatchesLastOwn() {
    details.addToOwnResourceVersions("3");
    details.addToOwnResourceVersions("5");
    details.addRelatedEvent(updatedEvent("5", "4"));

    assertThat(details.newerOrEqualEventReceivedForOwnLastUpdate()).isTrue();
  }

  @Test
  void newerOrEqualReturnsTrueWhenRelatedNewerThanLastOwn() {
    details.addToOwnResourceVersions("3");
    details.addRelatedEvent(updatedEvent("7", "3"));

    assertThat(details.newerOrEqualEventReceivedForOwnLastUpdate()).isTrue();
  }

  @Test
  void summaryEventReturnsEmptyWhenNoRelatedEvents() {
    assertThat(details.summaryEvent()).isEmpty();
  }

  @Test
  void summaryEventForReListReturnsEmptyWhenNoRelatedEventsAndMarksSent() {
    var reListDetails = new EventFilterDetails(true);

    assertThat(reListDetails.summaryEventForReList()).isEmpty();
    assertThat(reListDetails.isReListSummaryEventSent()).isTrue();
  }

  @Test
  void summaryEventForReListReturnsSummaryAndMarksSent() {
    var reListDetails = new EventFilterDetails(true);
    var event = updatedEvent("3", "2");
    reListDetails.addRelatedEvent(event);

    var summary = reListDetails.summaryEventForReList();

    assertThat(summary).contains(event);
    assertThat(reListDetails.isReListSummaryEventSent()).isTrue();
  }

  @Test
  void summaryEventForReListThrowsWhenNotAffectedByReList() {
    details.addRelatedEvent(updatedEvent("3", "2"));

    assertThatIllegalStateException().isThrownBy(() -> details.summaryEventForReList());
  }

  @Test
  void summaryEventForReListThrowsWhenAlreadySent() {
    var reListDetails = new EventFilterDetails(true);
    reListDetails.addRelatedEvent(updatedEvent("3", "2"));
    reListDetails.summaryEventForReList();

    assertThatIllegalStateException().isThrownBy(() -> reListDetails.summaryEventForReList());
  }

  @Test
  void affectedByReListFlagCanBeSet() {
    assertThat(details.isAffectedByReList()).isFalse();

    details.affectedByReList();

    assertThat(details.isAffectedByReList()).isTrue();
  }

  private static GenericResourceEvent addedEvent(String resourceVersion) {
    return new GenericResourceEvent(ResourceAction.ADDED, resource(resourceVersion), null, null);
  }

  private static GenericResourceEvent updatedEvent(
      String resourceVersion, String previousResourceVersion) {
    var prev = previousResourceVersion == null ? null : resource(previousResourceVersion);
    return new GenericResourceEvent(ResourceAction.UPDATED, resource(resourceVersion), prev, null);
  }

  private static GenericResourceEvent deleteEvent(String resourceVersion) {
    return new GenericResourceEvent(ResourceAction.DELETED, resource(resourceVersion), null, null);
  }

  private static ConfigMap resource(String resourceVersion) {
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName("test")
                .withNamespace("default")
                .withUid("test-uid")
                .withResourceVersion(resourceVersion)
                .build())
        .build();
  }
}
