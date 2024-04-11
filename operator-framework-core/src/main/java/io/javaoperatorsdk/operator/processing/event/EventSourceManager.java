package io.javaoperatorsdk.operator.processing.event;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.informer.ManagedInformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

public class EventSourceManager<P extends HasMetadata>
    implements LifecycleAware, EventSourceRetriever<P> {

  private static final Logger log = LoggerFactory.getLogger(EventSourceManager.class);

  private final EventSources<P> eventSources;
  private final Controller<P> controller;
  private final ExecutorServiceManager executorServiceManager;

  public EventSourceManager(Controller<P> controller) {
    this(controller, new EventSources<>());
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
    eventSources.controllerResourceEventSource().setEventHandler(controller.getEventProcessor());
    eventSources.retryEventSource().setEventHandler(controller.getEventProcessor());
  }

  /**
   * Starts the event sources first and then the processor. Note that it's not desired to start
   * processing events while the event sources are not "synced". This not fully started and the
   * caches propagated - although for non k8s related event sources this behavior might be different
   * (see
   * {@link io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource}).
   * <p>
   * Now the event sources are also started sequentially, mainly because others might depend on
   * {@link ControllerResourceEventSource} , which is started first.
   */
  @Override
  public synchronized void start() {
    startEventSource(eventSources.namedControllerResourceEventSource());

    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        eventSources.additionalNamedEventSources()
            .filter(es -> es.priority().equals(EventSourceStartPriority.RESOURCE_STATE_LOADER)),
        this::startEventSource,
        getThreadNamer("start"));

    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        eventSources.additionalNamedEventSources()
            .filter(es -> es.priority().equals(EventSourceStartPriority.DEFAULT)),
        this::startEventSource,
        getThreadNamer("start"));
  }

  private static Function<NamedEventSource, String> getThreadNamer(String stage) {
    return es -> {
      final var name = es.name();
      return es.priority() + " " + stage + " -> "
          + (es.isNameSet() ? name + " " + es.original().getClass() : es.original());
    };
  }

  private static Function<NamespaceChangeable, String> getEventSourceThreadNamer(String stage) {
    return es -> stage + " -> " + es;
  }

  @Override
  public synchronized void stop() {
    stopEventSource(eventSources.namedControllerResourceEventSource());
    executorServiceManager.boundedExecuteAndWaitForAllToComplete(
        eventSources.additionalNamedEventSources(),
        this::stopEventSource,
        getThreadNamer("stop"));
  }

  @SuppressWarnings("rawtypes")
  private void logEventSourceEvent(NamedEventSource eventSource, String event) {
    if (log.isDebugEnabled()) {
      if (eventSource.original() instanceof ResourceEventSource source) {
        log.debug("{} event source {} for {}", event,
            eventSource.isNameSet() ? eventSource.name() : eventSource,
            source.resourceType());
      } else {
        log.debug("{} event source {}", event,
            eventSource.isNameSet() ? eventSource.name() : eventSource);
      }
    }
  }

  private Void startEventSource(NamedEventSource eventSource) {
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

  private Void stopEventSource(NamedEventSource eventSource) {
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
  public final synchronized void registerEventSource(EventSource eventSource)
      throws OperatorException {
    Objects.requireNonNull(eventSource, "EventSource must not be null");
    try {
      if (eventSource instanceof ManagedInformerEventSource managedInformerEventSource) {

        managedInformerEventSource.setConfigurationService(
            controller.getConfiguration().getConfigurationService());
      }
      final var named = new NamedEventSource(eventSource, eventSource.name());
      eventSources.add(named);
      named.setEventHandler(controller.getEventProcessor());
    } catch (IllegalStateException | MissingCRDException e) {
      throw e; // leave untouched
    } catch (Exception e) {
      throw new OperatorException("Couldn't register event source: " + eventSource.name() + " for "
          + controller.getConfiguration().getName() + " controller", e);
    }
  }

  @SuppressWarnings("unchecked")
  public void broadcastOnResourceEvent(ResourceAction action, P resource, P oldResource) {
    eventSources.additionalNamedEventSources()
        .map(NamedEventSource::original)
        .forEach(source -> {
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
    eventSources.controllerResourceEventSource()
        .changeNamespaces(namespaces);
    executorServiceManager.boundedExecuteAndWaitForAllToComplete(eventSources
        .additionalEventSources()
        .filter(NamespaceChangeable.class::isInstance)
        .map(NamespaceChangeable.class::cast)
        .filter(NamespaceChangeable::allowsNamespaceChanges), e -> {
          e.changeNamespaces(namespaces);
          return null;
        },
        getEventSourceThreadNamer("changeNamespace"));
  }

  public Set<EventSource> getRegisteredEventSources() {
    return eventSources.flatMappedSources()
        .map(NamedEventSource::original)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public Map<String, EventSource> allEventSources() {
    return eventSources.allNamedEventSources().collect(Collectors.toMap(NamedEventSource::name,
        NamedEventSource::original));
  }

  @SuppressWarnings("unused")
  public Stream<? extends EventSourceMetadata> getNamedEventSourcesStream() {
    return eventSources.flatMappedSources();
  }

  public ControllerResourceEventSource<P> getControllerResourceEventSource() {
    return eventSources.controllerResourceEventSource();
  }

  public <R> List<ResourceEventSource<R, P>> getResourceEventSourcesFor(Class<R> dependentType) {
    return eventSources.getEventSources(dependentType);
  }

  @Override
  public EventSource dynamicallyRegisterEventSource(EventSource eventSource) {
    synchronized (this) {
      var actual = eventSources.existing(eventSource.name(), eventSource);
      if (actual != null) {
        eventSource = actual.eventSource();
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
  public synchronized Optional<EventSource> dynamicallyDeRegisterEventSource(String name) {
    EventSource es = eventSources.remove(name);
    if (es != null) {
      es.stop();
    }
    return Optional.ofNullable(es);
  }

  @Override
  public EventSourceContext<P> eventSourceContextForDynamicRegistration() {
    return controller.eventSourceContext();
  }

  /**
   * @deprecated Use {@link #getResourceEventSourceFor(Class)} instead
   *
   * @param <R> target resource type
   * @param dependentType target resource class
   * @return list of related event sources
   */
  @Deprecated
  public <R> List<ResourceEventSource<R, P>> getEventSourcesFor(Class<R> dependentType) {
    return getResourceEventSourcesFor(dependentType);
  }

  @Override
  public <R> ResourceEventSource<R, P> getResourceEventSourceFor(
      Class<R> dependentType, String qualifier) {
    Objects.requireNonNull(dependentType, "dependentType is Mandatory");
    return eventSources.get(dependentType, qualifier);
  }

  TimerEventSource<P> retryEventSource() {
    return eventSources.retryEventSource();
  }

  Controller<P> getController() {
    return controller;
  }
}
