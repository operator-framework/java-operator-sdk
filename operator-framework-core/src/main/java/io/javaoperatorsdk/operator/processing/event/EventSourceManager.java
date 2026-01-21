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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.config.NamespaceChangeable;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceStartPriority;
import io.javaoperatorsdk.operator.processing.event.source.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

public class EventSourceManager<P extends HasMetadata>
    implements LifecycleAware, EventSourceRetriever<P> {

  private static final Logger log = LoggerFactory.getLogger(EventSourceManager.class);

  private final EventSources<P> eventSources;
  private final Controller<P> controller;
  private final ExecutorServiceManager executorServiceManager;

  public EventSourceManager(Controller<P> controller) {
    this(
        controller,
        new EventSources<>(controller.getConfiguration().triggerReconcilerOnAllEvents()));
  }

  EventSourceManager(Controller<P> controller, EventSources<P> eventSources) {
    this.eventSources = eventSources;
    this.controller = controller;
    this.executorServiceManager = controller.getExecutorServiceManager();
    // controller event source needs to be available before we create the event processor
    eventSources.createControllerEventSource(controller);
    postProcessDefaultEventSourcesAfterProcessorInitializer();
  }

  public void postProcessDefaultEventSourcesAfterProcessorInitializer() {
    eventSources.controllerEventSource().setEventHandler(controller.getEventProcessor());
    eventSources.retryEventSource().setEventHandler(controller.getEventProcessor());
  }

  /**
   * Starts the event sources first and then the processor. Note that it's not desired to start
   * processing events while the event sources are not "synced". This not fully started and the
   * caches propagated - although for non k8s related event sources this behavior might be different
   * (see {@link
   * io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource}).
   *
   * <p>Now the event sources are also started sequentially, mainly because others might depend on
   * {@link ControllerEventSource} , which is started first.
   */
  @Override
  public synchronized void start() {
    startEventSource(eventSources.controllerEventSource());

    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        eventSources
            .additionalEventSources()
            .filter(es -> es.priority().equals(EventSourceStartPriority.RESOURCE_STATE_LOADER)),
        this::startEventSource,
        getThreadNamer("start"));

    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        eventSources
            .additionalEventSources()
            .filter(es -> es.priority().equals(EventSourceStartPriority.DEFAULT)),
        this::startEventSource,
        getThreadNamer("start"));
  }

  @SuppressWarnings("rawtypes")
  private static Function<EventSource, String> getThreadNamer(String stage) {
    return es -> es.priority() + " " + stage + " -> " + es.name();
  }

  private static Function<NamespaceChangeable, String> getEventSourceThreadNamer(String stage) {
    return es -> stage + " -> " + es;
  }

  @Override
  public synchronized void stop() {
    stopEventSource(eventSources.controllerEventSource());
    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        eventSources.additionalEventSources(), this::stopEventSource, getThreadNamer("stop"));
  }

  @SuppressWarnings("rawtypes")
  private void logEventSourceEvent(EventSource eventSource, String event) {
    if (log.isDebugEnabled()) {
      log.debug("{} event source {} for {}", event, eventSource.name(), eventSource.resourceType());
    }
  }

  private <R> Void startEventSource(EventSource<R, P> eventSource) {
    try {
      logEventSourceEvent(eventSource, "Starting");
      eventSource.start();
      logEventSourceEvent(eventSource, "Started");
    } catch (MissingCRDException e) {
      throw e; // leave untouched
    } catch (Exception e) {
      throw new OperatorException("Couldn't start source " + eventSource.name(), e);
    }
    return null;
  }

  private <R> Void stopEventSource(EventSource<R, P> eventSource) {
    try {
      logEventSourceEvent(eventSource, "Stopping");
      eventSource.stop();
      logEventSourceEvent(eventSource, "Stopped");
    } catch (Exception e) {
      log.warn("Error closing {} -> {}", eventSource.name(), e);
    }
    return null;
  }

  @SuppressWarnings("rawtypes")
  public final synchronized <R> void registerEventSource(EventSource<R, P> eventSource)
      throws OperatorException {
    Objects.requireNonNull(eventSource, "EventSource must not be null");
    try {
      if (eventSource instanceof ManagedInformerEventSource managedInformerEventSource) {
        managedInformerEventSource.setControllerConfiguration(controller.getConfiguration());
      }
      eventSources.add(eventSource);
      eventSource.setEventHandler(controller.getEventProcessor());
    } catch (IllegalStateException | MissingCRDException e) {
      throw e; // leave untouched
    } catch (Exception e) {
      throw new OperatorException(
          "Couldn't register event source: "
              + eventSource.name()
              + " for "
              + controller.getConfiguration().getName()
              + " controller",
          e);
    }
  }

  @SuppressWarnings("unchecked")
  public void broadcastOnResourceEvent(ResourceAction action, P resource, P oldResource) {
    eventSources
        .additionalEventSources()
        .forEach(
            source -> {
              if (source instanceof ResourceEventAware) {
                var lifecycleAwareES = ((ResourceEventAware<P>) source);
                switch (action) {
                  case ADDED:
                    lifecycleAwareES.onResourceCreated(resource);
                    break;
                  case UPDATED:
                    lifecycleAwareES.onResourceUpdated(resource, oldResource);
                    break;
                  case DELETED:
                    lifecycleAwareES.onResourceDeleted(resource);
                    break;
                }
              }
            });
  }

  public void changeNamespaces(Set<String> namespaces) {
    eventSources.controllerEventSource().changeNamespaces(namespaces);
    final var namespaceChangeables =
        eventSources
            .additionalEventSources()
            .filter(NamespaceChangeable.class::isInstance)
            .map(NamespaceChangeable.class::cast)
            .filter(NamespaceChangeable::allowsNamespaceChanges);
    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        namespaceChangeables,
        e -> {
          e.changeNamespaces(namespaces);
          return null;
        },
        getEventSourceThreadNamer("changeNamespace"));
  }

  public Set<EventSource<?, P>> getRegisteredEventSources() {
    return eventSources.flatMappedSources().collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @SuppressWarnings("rawtypes")
  public List<EventSource> allEventSources() {
    return eventSources.allEventSources().toList();
  }

  @SuppressWarnings("unused")
  public Stream<? extends EventSource<?, P>> getEventSourcesStream() {
    return eventSources.flatMappedSources();
  }

  @Override
  public ControllerEventSource<P> getControllerEventSource() {
    return eventSources.controllerEventSource();
  }

  public <R> List<EventSource<R, P>> getEventSourcesFor(Class<R> dependentType) {
    return eventSources.getEventSources(dependentType);
  }

  @Override
  public <R> EventSource<R, P> dynamicallyRegisterEventSource(EventSource<R, P> eventSource) {
    synchronized (this) {
      var actual = eventSources.existingEventSourceByName(eventSource.name());
      if (actual != null) {
        eventSource = actual;
      } else {
        registerEventSource(eventSource);
      }
    }
    // The start itself is blocking thus blocking only the threads which are attempt to start the
    // actual event source. Think of this as a form of lock striping.
    eventSource.start();
    return eventSource;
  }

  @Override
  public synchronized <R> Optional<EventSource<R, P>> dynamicallyDeRegisterEventSource(
      String name) {
    @SuppressWarnings("unchecked")
    EventSource<R, P> es = eventSources.remove(name);
    if (es != null) {
      es.stop();
    }
    return Optional.ofNullable(es);
  }

  @Override
  public EventSourceContext<P> eventSourceContextForDynamicRegistration() {
    return controller.eventSourceContext();
  }

  @Override
  public <R> EventSource<R, P> getEventSourceFor(Class<R> dependentType, String name) {
    Objects.requireNonNull(dependentType, "dependentType is Mandatory");
    return eventSources.get(dependentType, name);
  }

  TimerEventSource<P> retryEventSource() {
    return eventSources.retryEventSource();
  }

  Controller<P> getController() {
    return controller;
  }
}
