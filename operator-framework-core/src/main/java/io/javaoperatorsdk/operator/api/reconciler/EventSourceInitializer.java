package io.javaoperatorsdk.operator.api.reconciler;

import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

/**
 * An interface that a {@link Reconciler} can implement to have the SDK register the provided
 * {@link EventSource}
 * 
 * @param <P> the primary resource type handled by the associated {@link Reconciler}
 */
public interface EventSourceInitializer<P extends HasMetadata> {

  /**
   * Prepares a list of {@link EventSource} implementations to be registered by the SDK.
   * 
   * @param context a {@link EventSourceContext} providing access to information useful to event
   *        sources
   * @return list of event sources to register
   */
  List<EventSource> prepareEventSources(EventSourceContext<P> context);

}
