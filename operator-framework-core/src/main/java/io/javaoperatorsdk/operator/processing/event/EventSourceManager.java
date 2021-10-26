package io.javaoperatorsdk.operator.processing.event;

import java.util.Set;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;

public interface EventSourceManager<T extends CustomResource<?, ?>> {

  /**
   * Add the {@link EventSource} identified by the given <code>name</code> to the event manager.
   *
   * @param eventSource the {@link EventSource} to register
   * @throws IllegalStateException if an {@link EventSource} with the same name is already
   *         registered.
   * @throws OperatorException if an error occurred during the registration process
   */
  void registerEventSource(EventSource eventSource)
      throws IllegalStateException, OperatorException;

  Set<EventSource> getRegisteredEventSources();

  CustomResourceEventSource<T> getCustomResourceEventSource();

}
