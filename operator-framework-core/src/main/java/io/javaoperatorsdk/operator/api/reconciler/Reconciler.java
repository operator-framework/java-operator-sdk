package io.javaoperatorsdk.operator.api.reconciler;

import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

public interface Reconciler<P extends HasMetadata> {

  /**
   * The implementation of this operation is required to be idempotent. Always use the UpdateControl
   * object to make updates on custom resource if possible.
   *
   * @throws Exception from the custom implementation
   * @param resource the resource that has been created or updated
   * @param context the context with which the operation is executed
   * @return UpdateControl to manage updates on the custom resource (usually the status) after
   *         reconciliation.
   */
  UpdateControl<P> reconcile(P resource, Context<P> context) throws Exception;


  /**
   * Prepares a map of {@link EventSource} implementations keyed by the name with which they need to
   * be registered by the SDK.
   *
   * @param context a {@link EventSourceContext} providing access to information useful to event
   *        sources
   * @return a map of event sources to register
   */
  default Map<String, EventSource> prepareEventSources(EventSourceContext<P> context) {
    return Map.of();
  }

  /**
   * Utility method to easily create map with generated name for event sources. This is for the use
   * case when the event sources are not access explicitly by name in the reconciler.
   *
   * @param eventSources to name
   * @return even source with default names
   */
  static Map<String, EventSource> nameEventSources(EventSource... eventSources) {
    Map<String, EventSource> eventSourceMap = new HashMap<>(eventSources.length);
    for (EventSource eventSource : eventSources) {
      eventSourceMap.put(generateNameFor(eventSource), eventSource);
    }
    return eventSourceMap;
  }

  @SuppressWarnings("unchecked")
  static <K extends HasMetadata> Map<String, EventSource> eventSourcesFromWorkflow(
      EventSourceContext<K> context,
      Workflow<K> workflow) {
    Map<String, EventSource> result = new HashMap<>();
    for (var e : workflow.getDependentResourcesByNameWithoutActivationCondition().entrySet()) {
      var eventSource = e.getValue().eventSource(context);
      eventSource.ifPresent(es -> result.put(e.getKey(), (EventSource) es));
    }
    return result;
  }

  @SuppressWarnings("rawtypes")
  static <K extends HasMetadata> Map<String, EventSource> nameEventSourcesFromDependentResource(
      EventSourceContext<K> context, DependentResource... dependentResources) {
    return nameEventSourcesFromDependentResource(context, Arrays.asList(dependentResources));
  }

  @SuppressWarnings("unchecked,rawtypes")
  static <K extends HasMetadata> Map<String, EventSource> nameEventSourcesFromDependentResource(
      EventSourceContext<K> context, Collection<DependentResource> dependentResources) {

    if (dependentResources != null) {
      Map<String, EventSource> eventSourceMap = new HashMap<>(dependentResources.size());
      for (DependentResource dependentResource : dependentResources) {
        Optional<ResourceEventSource> es = dependentResource.eventSource(context);
        es.ifPresent(e -> eventSourceMap.put(generateNameFor(e), e));
      }
      return eventSourceMap;
    } else {
      return Collections.emptyMap();
    }
  }

  /**
   * Used when event sources are not explicitly named when created/registered.
   *
   * @param eventSource EventSource
   * @return generated name
   */
  static String generateNameFor(EventSource eventSource) {
    // we can have multiple event sources for the same class
    return eventSource.getClass().getName() + "#" + eventSource.hashCode();
  }
}
