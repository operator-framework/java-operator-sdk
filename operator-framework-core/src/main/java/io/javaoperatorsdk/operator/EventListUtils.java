package io.javaoperatorsdk.operator;

import java.util.List;

import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;

public class EventListUtils {

  public static boolean containsCustomResourceDeletedEvent(List<Event> events) {
    return events.stream()
        .anyMatch(
            e -> {
              if (e instanceof CustomResourceEvent) {
                return ((CustomResourceEvent) e).getAction() == Watcher.Action.DELETED;
              } else {
                return false;
              }
            });
  }
}
