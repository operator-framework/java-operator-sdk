/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.event;

import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;

public interface EventSourceRetriever<P extends HasMetadata> {

  default <R> EventSource<R, P> getEventSourceFor(Class<R> dependentType) {
    return getEventSourceFor(dependentType, null);
  }

  <R> EventSource<R, P> getEventSourceFor(Class<R> dependentType, String name);

  <R> List<EventSource<R, P>> getEventSourcesFor(Class<R> dependentType);

  ControllerEventSource<P> getControllerEventSource();

  /**
   * Registers (and starts) the specified {@link EventSource} dynamically during the reconciliation.
   * If an EventSource is already registered with the specified name, the registration will be
   * ignored. It is the user's responsibility to handle the naming correctly.
   *
   * <p>This is only needed when your operator needs to adapt dynamically based on optional
   * resources that may or may not be present on the target cluster. Even in this situation, it
   * should be possible to make these decisions at when the "regular" EventSources are registered so
   * this method should not typically be called directly but rather by the framework to support
   * activation conditions of dependents, for example.
   *
   * <p>This method will block until the event source is synced (if needed, as it is the case for
   * {@link io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource}).
   *
   * <p><strong>IMPORTANT:</strong> Should multiple reconciliations happen concurrently, only one
   * EventSource with the specified name will ever be registered. It is therefore important to
   * explicitly name the event sources that you want to reuse because the name will be used to
   * identify which event sources need to be created or not. If you let JOSDK implicitly name event
   * sources, then you might end up with duplicated event sources because concurrent registration of
   * event sources will lead to 2 (or more) event sources for the same resource type to be attempted
   * to be registered under different, automatically generated names. If you clearly identify your
   * event sources with names, then, if the concurrent process determines that an event source with
   * the specified name, it won't register it again.
   *
   * @param eventSource to register
   * @return the actual event source registered. Might not be the same as the parameter.
   */
  <R> EventSource<R, P> dynamicallyRegisterEventSource(EventSource<R, P> eventSource);

  /**
   * De-registers (and stops) the {@link EventSource} associated with the specified name. If no such
   * source exists, this method will do nothing.
   *
   * <p>This method will block until the event source is de-registered and stopped. If multiple
   * reconciliations happen concurrently, all will be blocked until the event source is
   * de-registered.
   *
   * <p>This method is meant only to be used for dynamically registered event sources and should not
   * be typically called directly.
   *
   * @param name of the event source
   * @return the actual event source deregistered if there is one.
   */
  <R> Optional<EventSource<R, P>> dynamicallyDeRegisterEventSource(String name);

  EventSourceContext<P> eventSourceContextForDynamicRegistration();
}
