package io.javaoperatorsdk.operator.processing.event;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.config.NamespaceChangeable;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;

public class EventSourceManager<R extends HasMetadata> implements LifecycleAware {

  private static final Logger log = LoggerFactory.getLogger(EventSourceManager.class);

  private final EventSources<R> eventSources;
  private final EventProcessor<R> eventProcessor;
  private final Controller<R> controller;

  public EventSourceManager(Controller<R> controller) {
    this(controller, new EventSources<>());
  }

  EventSourceManager(Controller<R> controller, EventSources<R> eventSources) {
    this.eventSources = eventSources;
    this.controller = controller;
    // controller event source needs to be available before we create the event processor
    eventSources.initControllerEventSource(controller);
    this.eventProcessor = new EventProcessor<>(this);

    postProcessDefaultEventSources();
  }

  private void postProcessDefaultEventSources() {
    eventSources.controllerResourceEventSource().setEventHandler(eventProcessor);
    eventSources.retryEventSource().setEventHandler(eventProcessor);
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
    eventSources.additionalNamedEventSources().parallel().forEach(this::startEventSource);
    eventProcessor.start();
  }

  @SuppressWarnings("rawtypes")


  @Override
  public synchronized void stop() {
    stopEventSource(eventSources.namedControllerResourceEventSource());
    eventSources.additionalNamedEventSources().parallel().forEach(this::stopEventSource);
    eventSources.clear();
    eventProcessor.stop();
  }

  @SuppressWarnings("rawtypes")
  private void logEventSourceEvent(NamedEventSource eventSource, String event) {
    if (log.isDebugEnabled()) {
      if (eventSource instanceof ResourceEventSource) {
        ResourceEventSource source = (ResourceEventSource) eventSource;
        log.debug("{} event source {} for {}", event, eventSource.name(),
            source.resourceType());
      } else {
        log.debug("{} event source {}", event, eventSource.name());
      }
    }
  }

  private void startEventSource(NamedEventSource eventSource) {
    try {
      logEventSourceEvent(eventSource, "Starting");
      eventSource.start();
      logEventSourceEvent(eventSource, "Started");
    } catch (MissingCRDException e) {
      throw e; // leave untouched
    } catch (Exception e) {
      throw new OperatorException("Couldn't start source " + eventSource.name(), e);
    }
  }

  private void stopEventSource(NamedEventSource eventSource) {
    try {
      logEventSourceEvent(eventSource, "Stopping");
      eventSource.stop();
      logEventSourceEvent(eventSource, "Stopped");
    } catch (Exception e) {
      log.warn("Error closing {} -> {}", eventSource.name(), e);
    }
  }

  public final void registerEventSource(EventSource eventSource) throws OperatorException {
    registerEventSource(null, eventSource);
  }

  public final synchronized void registerEventSource(String name, EventSource eventSource)
      throws OperatorException {
    Objects.requireNonNull(eventSource, "EventSource must not be null");
    try {
      if (name == null || name.isBlank()) {
        name = EventSourceInitializer.generateNameFor(eventSource);
      }
      eventSources.add(name, eventSource);
      eventSource.setEventHandler(eventProcessor);
    } catch (IllegalStateException | MissingCRDException e) {
      throw e; // leave untouched
    } catch (Exception e) {
      throw new OperatorException("Couldn't register event source: " + name + " for "
          + controller.getConfiguration().getName() + " controller`", e);
    }
  }

  @SuppressWarnings("unchecked")
  public void broadcastOnResourceEvent(ResourceAction action, R resource, R oldResource) {
    eventSources.additionalNamedEventSources().forEach(eventSource -> {
      if (eventSource instanceof ResourceEventAware) {
        var lifecycleAwareES = ((ResourceEventAware<R>) eventSource);
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
    eventProcessor.stop();
    eventSources.controllerResourceEventSource()
        .changeNamespaces(namespaces);
    eventSources
        .additionalEventSources()
        .filter(NamespaceChangeable.class::isInstance)
        .map(NamespaceChangeable.class::cast)
        .filter(NamespaceChangeable::allowsNamespaceChanges)
        .parallel()
        .forEach(ies -> ies.changeNamespaces(namespaces));
    eventProcessor.start();
  }

  EventHandler getEventHandler() {
    return eventProcessor;
  }

  public Set<EventSource> getRegisteredEventSources() {
    return eventSources.flatMappedSources()
        .map(NamedEventSource::original)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public ControllerResourceEventSource<R> getControllerResourceEventSource() {
    return eventSources.controllerResourceEventSource();
  }

  <S> ResourceEventSource<S, R> getResourceEventSourceFor(
      Class<S> dependentType) {
    return getResourceEventSourceFor(dependentType, null);
  }

  public <S> List<ResourceEventSource<S, R>> getEventSourcesFor(Class<S> dependentType) {
    return eventSources.getEventSources(dependentType);
  }

  public <S> ResourceEventSource<S, R> getResourceEventSourceFor(
      Class<S> dependentType, String qualifier) {
    Objects.requireNonNull(dependentType, "dependentType is Mandatory");
    return eventSources.get(dependentType, qualifier);
  }

  TimerEventSource<R> retryEventSource() {
    return eventSources.retryEventSource();
  }

  Controller<R> getController() {
    return controller;
  }
}
