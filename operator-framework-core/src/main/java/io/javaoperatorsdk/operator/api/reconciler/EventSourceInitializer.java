package io.javaoperatorsdk.operator.api.reconciler;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventSourceRegistry;

public interface EventSourceInitializer<T extends CustomResource<?, ?>> {

  /**
   * In this typically you might want to register event sources. But can access
   * CustomResourceEventSource, what might be handy for some edge cases.
   * 
   * @param eventSourceRegistry the {@link EventSourceRegistry} where event sources can be
   *        registered.
   */
  void prepareEventSources(EventSourceRegistry<T> eventSourceRegistry);

}
