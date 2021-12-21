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
   * @param primaryCache a cache providing direct access to primary resources so that event sources
   *        can extract relevant information from primary resources as needed
   */
  List<EventSource> prepareEventSources(EventSourceInitializationContext<P> context);

}
