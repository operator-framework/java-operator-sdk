package io.javaoperatorsdk.operator.processing.event;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventMarkerTest {

  private final EventMarker eventMarker = new EventMarker();
  private ObjectKey sampleObjectKey = new ObjectKey("test-name");
  private ObjectKey sampleObjectKey2 = new ObjectKey("test-name2");

  @Test
  public void returnsNoEventPresentIfNotMarkedYet() {
    assertThat(eventMarker.noEventPresent(sampleObjectKey)).isTrue();
  }

  @Test
  public void marksEvent() {
    eventMarker.markEventReceived(sampleObjectKey);

    assertThat(eventMarker.eventPresent(sampleObjectKey)).isTrue();
    assertThat(eventMarker.deleteEventPresent(sampleObjectKey)).isFalse();
  }

  @Test
  public void marksDeleteEvent() {
    eventMarker.markDeleteEventReceived(sampleObjectKey);

    assertThat(eventMarker.deleteEventPresent(sampleObjectKey))
        .isTrue();
    assertThat(eventMarker.eventPresent(sampleObjectKey)).isFalse();
  }

  @Test
  public void afterDeleteEventMarkEventIsNotRelevant() {
    eventMarker.markEventReceived(sampleObjectKey);

    eventMarker.markDeleteEventReceived(sampleObjectKey);

    assertThat(eventMarker.deleteEventPresent(sampleObjectKey))
        .isTrue();
    assertThat(eventMarker.eventPresent(sampleObjectKey)).isFalse();
  }

  @Test
  public void cleansUp() {
    eventMarker.markEventReceived(sampleObjectKey);
    eventMarker.markDeleteEventReceived(sampleObjectKey);

    eventMarker.cleanup(sampleObjectKey);

    assertThat(eventMarker.deleteEventPresent(sampleObjectKey)).isFalse();
    assertThat(eventMarker.eventPresent(sampleObjectKey)).isFalse();
  }

  @Test
  public void cannotMarkEventAfterDeleteEventReceived() {
    Assertions.assertThrows(IllegalStateException.class, () -> {
      eventMarker.markDeleteEventReceived(sampleObjectKey);
      eventMarker.markEventReceived(sampleObjectKey);
    });
  }

  @Test
  public void listsResourceIDSWithEventsPresent() {
    eventMarker.markEventReceived(sampleObjectKey);
    eventMarker.markEventReceived(sampleObjectKey2);
    eventMarker.unMarkEventReceived(sampleObjectKey);

    var res = eventMarker.resourceIDsWithEventPresent();

    assertThat(res).hasSize(1);
    assertThat(res).contains(sampleObjectKey2);
  }

}
