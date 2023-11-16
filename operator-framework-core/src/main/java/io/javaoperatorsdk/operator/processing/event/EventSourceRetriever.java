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
   * Registers (and starts) event source dynamically during the reconciliation. if there is an event
   * source registered already with the selected name it will just skip the registration.
   * <p>
   * Normally this is not needed, just in very special cases. Like when you are implementing an
   * operator that dynamically decides what resource it will watch or not. This is especially
   * important when the platform might or might not have such resources, and even that decision can
   * be done when registering event sources using the standard way. In other words, use this as a
   * last effort.
   * </p>
   * <p>
   * This method will block until the event source is synced (in case of InformersEventSource-s
   * mostly).
   * </p>
   * <p>
   * In case multiple reconciliations will happen concurrently, there will be nore more event
   * sources with same name registered, always only one.
   * </p>
   *
   * @param name of the event source
   * @param eventSource to register
   */
  void dynamicallyRegisterEventSource(String name, EventSource eventSource);

  /**
   * De-registers (and stops) an event source dynamically. If there is no event source with the
   * target name method will just return.
   * <p>
   * The method call will block until the event source is de-registered and stopped. If multiple
   * reconciliations are calling the method concurrently all will be blocked until the event source
   * if not de-registered.
   * </p>
   * <p>
   * This method is ment only to be used for dynamically registered event sources.
   * </p>
   *
   * @param name of the event source
   */
  void dynamicallyDeRegisterEventSource(String name);

  EventSourceContext<P> eventSourceContexForDynamicRegistration();

}
