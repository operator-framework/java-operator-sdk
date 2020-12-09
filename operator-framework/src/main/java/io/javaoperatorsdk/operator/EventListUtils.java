package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.Watcher;
import io.javaoperatorsdk.operator.api.Event;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEvent;
import java.util.List;

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
