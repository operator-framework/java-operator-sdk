package io.javaoperatorsdk.operator.processing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

import static org.assertj.core.api.Assertions.assertThat;

class EventMarkerTest {

  private final EventMarker eventMarker = new EventMarker();
  private CustomResourceID sampleCustomResourceID = new CustomResourceID("test-name");

  @Test
  public void returnsNoEventPresentIfNotMarkedYet() {
    assertThat(eventMarker.noEventPresent(sampleCustomResourceID)).isTrue();
  }

  @Test
  public void marksEvent() {
    eventMarker.markEventReceived(sampleCustomResourceID);

    assertThat(eventMarker.eventPresent(sampleCustomResourceID)).isTrue();
    assertThat(eventMarker.deleteEventPresent(sampleCustomResourceID)).isFalse();
  }

  @Test
  public void marksDeleteEvent() {
    eventMarker.markDeleteEventReceived(sampleCustomResourceID);

    assertThat(eventMarker.deleteEventPresent(sampleCustomResourceID))
        .isTrue();
    assertThat(eventMarker.eventPresent(sampleCustomResourceID)).isFalse();
  }

  @Test
  public void afterDeleteEventMarkEventIsNotRelevant() {
    eventMarker.markEventReceived(sampleCustomResourceID);

    eventMarker.markDeleteEventReceived(sampleCustomResourceID);

    assertThat(eventMarker.deleteEventPresent(sampleCustomResourceID))
        .isTrue();
    assertThat(eventMarker.eventPresent(sampleCustomResourceID)).isFalse();
  }

  @Test
  public void cleansUp() {
    eventMarker.markEventReceived(sampleCustomResourceID);
    eventMarker.markDeleteEventReceived(sampleCustomResourceID);

    eventMarker.cleanup(sampleCustomResourceID);

    assertThat(eventMarker.deleteEventPresent(sampleCustomResourceID)).isFalse();
    assertThat(eventMarker.eventPresent(sampleCustomResourceID)).isFalse();
  }

  @Test
  public void cannotMarkEventAfterDeleteEventReceived() {
    Assertions.assertThrows(IllegalStateException.class, () -> {
      eventMarker.markDeleteEventReceived(sampleCustomResourceID);
      eventMarker.markEventReceived(sampleCustomResourceID);
    });
  }

}
