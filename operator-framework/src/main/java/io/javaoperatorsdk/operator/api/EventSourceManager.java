package io.javaoperatorsdk.operator.api;

import java.util.Map;
import java.util.Optional;

public interface EventSourceManager {

  <T extends EventSource> void registerEventSource(String name, T eventSource);

  Optional<EventSource> deRegisterCustomResourceFromEventSource(
      String name, String customResourceUid);

  Map<String, EventSource> getRegisteredEventSources();
}
