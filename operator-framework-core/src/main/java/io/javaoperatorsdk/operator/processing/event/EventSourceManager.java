package io.javaoperatorsdk.operator.processing.event;

import java.util.Map;
import java.util.Optional;

public interface EventSourceManager {

  /**
   * Add the {@link EventSource} identified by the given <code>name</code> to the event manager.
   *
   * @param name the name of the {@link EventSource} to add
   * @param eventSource the {@link EventSource} to register
   * @thorw IllegalStateException if an {@link EventSource} with the same name is already
   *     registered.
   */
  void registerEventSource(String name, EventSource eventSource);

  /**
   * Remove the {@link EventSource} identified by the given <code>name</code> from the event
   * manager.
   *
   * @param name the name of the {@link EventSource} to remove
   * @return an optional {@link EventSource} which would be empty if no {@link EventSource} have
   *     been registered with the given name.
   */
  Optional<EventSource> deRegisterEventSource(String name);

  Optional<EventSource> deRegisterCustomResourceFromEventSource(
      String name, String customResourceUid);

  Map<String, EventSource> getRegisteredEventSources();
}
