package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

/**
 * An interface that a {@link Reconciler} can implement to have the SDK register the provided
 * {@link EventSource}
 *
 * @param <P> the primary resource type handled by the associated {@link Reconciler}
 */
public interface EventSourceInitializer<P extends HasMetadata> {

  /**
   * Prepares a map of {@link EventSource} implementations keyed by the name with which they need to
   * be registered by the SDK.
   *
   * @param context a {@link EventSourceContext} providing access to information useful to event
   *        sources
   * @return a map of event sources to register
   */
  Map<String, EventSource> prepareEventSources(EventSourceContext<P> context);

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

  @SuppressWarnings("unchecked,rawtypes")
  static <K extends HasMetadata> Map<String, EventSource> nameEventSourcesFromDependentResource(
      EventSourceContext<K> context, DependentResource... dependentResources) {

    if (dependentResources != null) {
      Map<String, EventSource> eventSourceMap = new HashMap<>(dependentResources.length);
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
