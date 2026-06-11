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
    assertThat(support.getActiveUpdates()).containsOnlyKeys(RESOURCE_ID);
  }

  @Test
  void startEventFilteringTwiceReusesEventingDetail() {
    support.startEventFilteringModify(RESOURCE_ID);
    var first = support.getActiveUpdates().get(RESOURCE_ID);

    support.startEventFilteringModify(RESOURCE_ID);
    var second = support.getActiveUpdates().get(RESOURCE_ID);

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
    support.processRelevantEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION));

    var res = support.doneEventFilterModify(RESOURCE_ID);

    assertThat(res).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void processRelevantEventPropagatesWhenNoEventingDetail() {
    var event = updateEvent(FIRST_OWN_VERSION);

    var res = support.processRelevantEvent(RESOURCE_ID, event);

    assertThat(res).contains(event);
  }

  @Test
  void processRelevantEventHoldsOwnEcho() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    var res = support.processRelevantEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION));

    assertThat(res).isEmpty();
  }

  @Test
  void processRelevantEventEmitsSynthForForeignEvent() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    support.processRelevantEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION - 1));

    var res = support.processRelevantEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION));

    assertThat(res).hasValueSatisfying(e -> assertThat(e.getAction()).isEqualTo(UPDATED));
  }

  @Test
  void processRelevantEventEmitsAddedForeignVerbatim() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    var added = addEvent(FIRST_OWN_VERSION + 1);
    var res = support.processRelevantEvent(RESOURCE_ID, added);

    assertThat(res).contains(added);
  }

  @Test
  void addToOwnResourceVersionsIsNoOpWithoutEventingDetail() {
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void handleGhostResourceRemovalDropsEventingDetail() {
    support.startEventFilteringModify(RESOURCE_ID);

    support.handleGhostResourceRemoval(RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void independentResourcesAreTrackedSeparately() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.startEventFilteringModify(OTHER_RESOURCE_ID);

    support.handleGhostResourceRemoval(RESOURCE_ID);

    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
    assertThat(support.isActiveUpdateFor(OTHER_RESOURCE_ID)).isTrue();
  }

  @Test
  void fullLifecycleOwnWriteOnlyEmitsNothingAndCleansUp() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));
    assertThat(support.processRelevantEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION))).isEmpty();

    var res = support.doneEventFilterModify(RESOURCE_ID);

    assertThat(res).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  @Test
  void fullLifecycleForeignBeforeOwnEchoEmitsSynth() {
    support.startEventFilteringModify(RESOURCE_ID);
    support.addToOwnResourceVersions(RESOURCE_ID, s(FIRST_OWN_VERSION));

    var foreign = updateEvent(FIRST_OWN_VERSION - 1);
    assertThat(support.processRelevantEvent(RESOURCE_ID, foreign)).contains(foreign);

    // catch-up emit triggered by the own echo arriving after the prior emit
    assertThat(support.processRelevantEvent(RESOURCE_ID, updateEvent(FIRST_OWN_VERSION)))
        .isPresent();
    assertThat(support.doneEventFilterModify(RESOURCE_ID)).isEmpty();
    assertThat(support.isActiveUpdateFor(RESOURCE_ID)).isFalse();
  }

  GenericResourceEvent updateEvent(long version) {
    return new GenericResourceEvent(
        UPDATED, testResource(version), testResource(version - 1), null);
  }

  GenericResourceEvent addEvent(long version) {
    return new GenericResourceEvent(ADDED, testResource(version), null, null);
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
