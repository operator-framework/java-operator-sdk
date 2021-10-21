package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

public interface EventSourceInitializer<T extends CustomResource<?, ?>> {

  /**
   * In this typically you might want to register event sources. But can access
   * CustomResourceEventSource, what might be handy for some edge cases.
   * 
   * @param eventSourceManager the {@link EventSourceManager} where event sources can be registered.
   */
  void prepareEventSources(EventSourceManager<T> eventSourceManager);

}
