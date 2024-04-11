package io.javaoperatorsdk.operator.api.reconciler;

import java.util.*;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Workflow;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

// todo cleanup / delete
public class EventSourceUtils {

  @SuppressWarnings("unchecked")
  public static  <R extends HasMetadata> List<EventSource> dependentEventSources(EventSourceContext<R> eventSourceContext,DependentResource... dependentResources) {
      return Arrays.stream(dependentResources)
              .flatMap(dr-> dr.eventSource(eventSourceContext).stream()).toList();
  }

  /**
   * Utility method to easily create map with generated name for event sources. This is for the use
   * case when the event sources are not access explicitly by name in the reconciler.
   *
   * @param eventSources to name
   * @return even source with default names
   */
  public static Map<String, EventSource> nameEventSources(EventSource... eventSources) {
    Map<String, EventSource> eventSourceMap = new HashMap<>(eventSources.length);
    for (EventSource eventSource : eventSources) {
      eventSourceMap.put(generateNameFor(eventSource), eventSource);
    }
    return eventSourceMap;
  }

  @SuppressWarnings("unchecked")
  public static <K extends HasMetadata> List<EventSource> eventSourcesFromWorkflow(
      EventSourceContext<K> context,
      Workflow<K> workflow) {
    return workflow.getDependentResourcesByNameWithoutActivationCondition().stream()
            .flatMap(dr->dr.eventSource(context).stream()).toList();
  }

  @SuppressWarnings("rawtypes")
  public static <K extends HasMetadata> Map<String, EventSource> nameEventSourcesFromDependentResource(
      EventSourceContext<K> context, DependentResource... dependentResources) {
    return nameEventSourcesFromDependentResource(context, Arrays.asList(dependentResources));
  }

  @SuppressWarnings("unchecked,rawtypes")
  public static <K extends HasMetadata> Map<String, EventSource> nameEventSourcesFromDependentResource(
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
  public static String generateNameFor(EventSource eventSource) {
    // we can have multiple event sources for the same class
    return eventSource.getClass().getName() + "#" + eventSource.hashCode();
  }
}
