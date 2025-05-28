package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    state.markDeleteEventReceived();

    assertThat(state.deleteEventPresent()).isTrue();
    assertThat(state.eventPresent()).isFalse();
  }

  @Test
  public void afterDeleteEventMarkEventIsNotRelevant() {
    state.markEventReceived();

    state.markDeleteEventReceived();

    assertThat(state.deleteEventPresent()).isTrue();
    assertThat(state.eventPresent()).isFalse();
  }

  @Test
  public void cleansUp() {
    state.markEventReceived();
    state.markDeleteEventReceived();

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
          state.markDeleteEventReceived();
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
}
