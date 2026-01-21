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
package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceStateManagerTest {

  private final ResourceStateManager manager = new ResourceStateManager();
  private final ResourceID sampleResourceID = new ResourceID("test-name");
  private final ResourceID sampleResourceID2 = new ResourceID("test-name2");
  private ResourceState state;
  private ResourceState state2;

  @BeforeEach
  void init() {
    manager.remove(sampleResourceID);
    manager.remove(sampleResourceID2);

    state = manager.getOrCreate(sampleResourceID);
    state2 = manager.getOrCreate(sampleResourceID2);
  }

  @Test
  public void returnsNoEventPresentIfNotMarkedYet() {
    assertThat(state.noEventPresent()).isTrue();
  }

  @Test
  public void marksEvent() {
    state.markEventReceived(false);

    assertThat(state.eventPresent()).isTrue();
    assertThat(state.deleteEventPresent()).isFalse();
  }

  @Test
  public void marksDeleteEvent() {
    state.markDeleteEventReceived(TestUtils.testCustomResource(), true);

    assertThat(state.deleteEventPresent()).isTrue();
    assertThat(state.eventPresent()).isFalse();
  }

  @Test
  public void afterDeleteEventMarkEventIsNotRelevant() {
    state.markEventReceived(false);

    state.markDeleteEventReceived(TestUtils.testCustomResource(), true);

    assertThat(state.deleteEventPresent()).isTrue();
    assertThat(state.eventPresent()).isFalse();
  }

  @Test
  public void cleansUp() {
    state.markEventReceived(false);
    state.markDeleteEventReceived(TestUtils.testCustomResource(), true);

    manager.remove(sampleResourceID);

    state = manager.getOrCreate(sampleResourceID);
    assertThat(state.deleteEventPresent()).isFalse();
    assertThat(state.eventPresent()).isFalse();
  }

  @Test
  public void cannotMarkEventAfterDeleteEventReceived() {
    Assertions.assertThrows(
        IllegalStateException.class,
        () -> {
          state.markDeleteEventReceived(TestUtils.testCustomResource(), true);
          state.markEventReceived(false);
        });
  }

  @Test
  public void listsResourceIDSWithEventsPresent() {
    state.markEventReceived(false);
    state2.markEventReceived(false);
    state.unMarkEventReceived(false);

    var res = manager.resourcesWithEventPresent();

    assertThat(res).hasSize(1);
    assertThat(res.get(0).getId()).isEqualTo(sampleResourceID2);
  }

  @Test
  void createStateOnlyOnResourceEvent() {
    var state = manager.getOrCreateOnResourceEvent(new Event(new ResourceID("newEvent")));

    assertThat(state).isEmpty();

    state =
        manager.getOrCreateOnResourceEvent(
            new ResourceEvent(
                ResourceAction.ADDED, new ResourceID("newEvent"), TestUtils.testCustomResource()));

    assertThat(state).isNotNull();
  }

  @Test
  void createsOnlyResourceEventReturnsPreviouslyCreatedState() {
    manager.getOrCreate(new ResourceID("newEvent"));

    var res = manager.getOrCreateOnResourceEvent(new Event(new ResourceID("newEvent")));
    assertThat(res).isNotNull();
  }
}
