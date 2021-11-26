package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventMarkerTest {

  private final EventMarker eventMarker = new EventMarker();
  private ResourceID sampleResourceID = new ResourceID("test-name");

  @Test
  public void returnsNoEventPresentIfNotMarkedYet() {
    assertThat(eventMarker.noEventPresent(sampleResourceID)).isTrue();
  }

  @Test
  public void marksEvent() {
    eventMarker.markEventReceived(sampleResourceID);

    assertThat(eventMarker.eventPresent(sampleResourceID)).isTrue();
    assertThat(eventMarker.deleteEventPresent(sampleResourceID)).isFalse();
  }

  @Test
  public void marksDeleteEvent() {
    eventMarker.markDeleteEventReceived(sampleResourceID);

    assertThat(eventMarker.deleteEventPresent(sampleResourceID))
        .isTrue();
    assertThat(eventMarker.eventPresent(sampleResourceID)).isFalse();
  }

  @Test
  public void afterDeleteEventMarkEventIsNotRelevant() {
    eventMarker.markEventReceived(sampleResourceID);

    eventMarker.markDeleteEventReceived(sampleResourceID);

    assertThat(eventMarker.deleteEventPresent(sampleResourceID))
        .isTrue();
    assertThat(eventMarker.eventPresent(sampleResourceID)).isFalse();
  }

  @Test
  public void cleansUp() {
    eventMarker.markEventReceived(sampleResourceID);
    eventMarker.markDeleteEventReceived(sampleResourceID);

    eventMarker.cleanup(sampleResourceID);

    assertThat(eventMarker.deleteEventPresent(sampleResourceID)).isFalse();
    assertThat(eventMarker.eventPresent(sampleResourceID)).isFalse();
  }

  @Test
  public void cannotMarkEventAfterDeleteEventReceived() {
    Assertions.assertThrows(IllegalStateException.class, () -> {
      eventMarker.markDeleteEventReceived(sampleResourceID);
      eventMarker.markEventReceived(sampleResourceID);
    });
  }

}
