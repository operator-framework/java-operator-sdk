package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.TestUtils;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceEvent;

import io.javaoperatorsdk.operator.TestUtils;

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
    state.markEventReceived();

    assertThat(state.eventPresent()).isTrue();
    assertThat(state.deleteEventPresent()).isFalse();
  }

  @Test
  public void marksDeleteEvent() {
    state.markDeleteEventReceived(TestUtils.testCustomResource());

    assertThat(state.deleteEventPresent()).isTrue();
    assertThat(state.eventPresent()).isFalse();
  }

  @Test
  public void afterDeleteEventMarkEventIsNotRelevant() {
    state.markEventReceived();

    state.markDeleteEventReceived(TestUtils.testCustomResource());

    assertThat(state.deleteEventPresent()).isTrue();
    assertThat(state.eventPresent()).isFalse();
  }

  @Test
  public void cleansUp() {
    state.markEventReceived();
    state.markDeleteEventReceived(TestUtils.testCustomResource());

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
          state.markDeleteEventReceived(TestUtils.testCustomResource());
          state.markEventReceived();
        });
  }

  @Test
  public void listsResourceIDSWithEventsPresent() {
    state.markEventReceived();
    state2.markEventReceived();
    state.unMarkEventReceived();

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
