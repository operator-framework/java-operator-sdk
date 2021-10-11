package io.javaoperatorsdk.operator.processing.event;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.processing.event.internal.CustomResourceEventSource;

public interface EventSourceManager<T extends CustomResource<?, ?>> extends Closeable {

  /**
   * Add the {@link EventSource} identified by the given <code>name</code> to the event manager.
   *
   * @param name the name of the {@link EventSource} to add
   * @param eventSource the {@link EventSource} to register
   * @throws IllegalStateException if an {@link EventSource} with the same name is already
   *         registered.
   * @throws OperatorException if an error occurred during the registration process
   */
  void registerEventSource(EventSource eventSource)
      throws IllegalStateException, OperatorException;

  /**
   * Remove the {@link EventSource} identified by the given <code>name</code> from the event
   * manager.
   *
   * @param name the name of the {@link EventSource} to remove
   * @return an optional {@link EventSource} which would be empty if no {@link EventSource} have
   *         been registered with the given name.
   */

  void deRegisterCustomResourceFromEventSources(CustomResourceID customResourceUid);

  List<EventSource> getRegisteredEventSources();

  CustomResourceEventSource<T> getCustomResourceEventSource();

  @Override
  default void close() throws IOException {}
}
