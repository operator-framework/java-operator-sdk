package io.javaoperatorsdk.operator;

import java.util.List;

import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import io.javaoperatorsdk.operator.processing.event.internal.ResourceAction;

public class EventListUtils {

  public static boolean containsCustomResourceDeletedEvent(List<Event> events) {
    return events.stream()
        .anyMatch(
            e -> {
              if (e instanceof CustomResourceEvent) {
                return ((CustomResourceEvent) e).getAction() == ResourceAction.DELETED;
              } else {
                return false;
              }
            });
  }
}
