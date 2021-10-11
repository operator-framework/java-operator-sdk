package io.javaoperatorsdk.operator.processing;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.processing.event.CustomResourceID;

import static io.javaoperatorsdk.operator.processing.EventMarker.EventingState.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class EventMarkerTest {

  private final EventMarker eventMarker = new EventMarker();
  private CustomResourceID sampleCustomResourceID = new CustomResourceID("test-name");

  @Test
  public void returnsNoEventPresentIfNotMarkedYet() {
    assertThat(eventMarker.getEventingState(sampleCustomResourceID)).isEqualTo(NO_EVENT_PRESENT);
  }

  @Test
  public void marksEvent() {
    eventMarker.markEventReceived(sampleCustomResourceID);

    assertThat(eventMarker.getEventingState(sampleCustomResourceID)).isEqualTo(EVENT_PRESENT);
    assertThat(eventMarker.isEventPresent(sampleCustomResourceID)).isTrue();
  }

  @Test
  public void markEventAwareOfDeleteEvent() {
    eventMarker.markDeleteEventReceived(sampleCustomResourceID);

    eventMarker.markEventReceived(sampleCustomResourceID);

    assertThat(eventMarker.getEventingState(sampleCustomResourceID))
        .isEqualTo(DELETE_AND_NON_DELETE_EVENT_PRESENT);
    assertThat(eventMarker.isEventPresent(sampleCustomResourceID)).isTrue();
  }

  @Test
  public void marksDeleteEvent() {
    eventMarker.markDeleteEventReceived(sampleCustomResourceID);

    assertThat(eventMarker.getEventingState(sampleCustomResourceID))
        .isEqualTo(ONLY_DELETE_EVENT_PRESENT);
    assertThat(eventMarker.isDeleteEventPresent(sampleCustomResourceID)).isTrue();
  }

  @Test
  public void markDeleteEventAwareOfEvent() {
    eventMarker.markEventReceived(sampleCustomResourceID);

    eventMarker.markDeleteEventReceived(sampleCustomResourceID);

    assertThat(eventMarker.getEventingState(sampleCustomResourceID))
        .isEqualTo(DELETE_AND_NON_DELETE_EVENT_PRESENT);
    assertThat(eventMarker.isDeleteEventPresent(sampleCustomResourceID)).isTrue();
  }

  @Test
  public void cleansUp() {
    eventMarker.markEventReceived(sampleCustomResourceID);
    eventMarker.markDeleteEventReceived(sampleCustomResourceID);

    eventMarker.cleanup(sampleCustomResourceID);

    assertThat(eventMarker.getEventingState(sampleCustomResourceID)).isEqualTo(NO_EVENT_PRESENT);
  }

}
