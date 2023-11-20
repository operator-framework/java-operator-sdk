package io.javaoperatorsdk.operator.processing.event;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

public interface EventSourceRetriever<P extends HasMetadata> {

  default <R> ResourceEventSource<R, P> getResourceEventSourceFor(Class<R> dependentType) {
    return getResourceEventSourceFor(dependentType, null);
  }

  <R> ResourceEventSource<R, P> getResourceEventSourceFor(Class<R> dependentType, String name);

  <R> List<ResourceEventSource<R, P>> getResourceEventSourcesFor(Class<R> dependentType);

  /**
   * Registers (and starts) the specified {@link EventSource} dynamically during the reconciliation.
   * If an EventSource is already registered with the specified name, the registration will be
   * ignored. It is the user's responsibility to handle the naming correctly, thus to not try to
   * register different event source with same name that is already registered.
   * <p>
   * This is only needed when your operator needs to adapt dynamically based on optional resources
   * that may or may not be present on the target cluster. Even in this situation, it should be
   * possible to make these decisions at when the "regular" EventSources are registered so this
   * method should not typically be called directly but rather by the framework to support
   * activation conditions of dependents, for example.
   * </p>
   * <p>
   * This method will block until the event source is synced, if needed (as is the case for
   * {@link io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}).
   * </p>
   * <p>
   * Should multiple reconciliations happen concurrently, only one EventSource with the specified
   * name will ever be registered.
   * </p>
   *
   * @param name of the event source
   * @param eventSource to register
   */
  void dynamicallyRegisterEventSource(String name, EventSource eventSource);

  /**
   * De-registers (and stops) the {@link EventSource} associated with the specified name. If no such
   * source exists, this method will do nothing.
   * <p>
   * This method will block until the event source is de-registered and stopped. If multiple
   * reconciliations happen concurrently, all will be blocked until the event source is
   * de-registered.
   * </p>
   * <p>
   * This method is meant only to be used for dynamically registered event sources and should not be
   * typically called directly.
   * </p>
   *
   * @param name of the event source
   */
  void dynamicallyDeRegisterEventSource(String name);

  EventSourceContext<P> eventSourceContexForDynamicRegistration();

}
