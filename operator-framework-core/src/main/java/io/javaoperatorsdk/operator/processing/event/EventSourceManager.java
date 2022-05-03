package io.javaoperatorsdk.operator.processing.event;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.MissingCRDException;
import io.javaoperatorsdk.operator.OperatorException;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventAware;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ControllerResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceAction;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
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
    final var controllerEventSource = eventSources.initControllerEventSource(controller);
    this.eventProcessor = new EventProcessor<>(this);

    // sources need to be registered after the event processor is created since it's set on the
    // event source
    registerEventSource(eventSources.retryEventSource());
    registerEventSource(controllerEventSource);
  }

  /**
   * Starts the event sources first and then the processor. Note that it's not desired to start
   * processing events while the event sources are not "synced". This not fully started and the
   * caches propagated - although for non k8s related event sources this behavior might be different
   * (see
   * {@link io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource}).
   *
   * Now the event sources are also started sequentially, mainly because others might depend on
   * {@link ControllerResourceEventSource} , which is started first.
   */
  @Override
  public synchronized void start() {
    for (var eventSource : eventSources) {
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
    eventProcessor.start();
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

  @Override
  public synchronized void stop() {
    for (var eventSource : eventSources) {
      try {
        logEventSourceEvent(eventSource, "Stopping");
        eventSource.stop();
        logEventSourceEvent(eventSource, "Stopped");
      } catch (Exception e) {
        log.warn("Error closing {} -> {}", eventSource.name(), e);
      }
    }
    eventSources.clear();
    eventProcessor.stop();
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
    for (var eventSource : eventSources) {
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
    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public void changeNamespaces(Set<String> namespaces) {
    eventProcessor.stop();
    getRegisteredEventSources().forEach(es -> {
      if (es instanceof InformerEventSource) {
        InformerEventSource ies = (InformerEventSource) es;
        if (ies.getConfiguration().followControllerNamespaceChanges()) {
          ies.changeNamespaces(namespaces);
        }
      }
    });
    getControllerResourceEventSource().changeNamespaces(namespaces);
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
