package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Map;

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
   * Prepares a map of {@link EventSource} implementations keyed by the name with which they need to
   * be registered by the SDK.
   *
   * @param context a {@link EventSourceContext} providing access to information useful to event
   *        sources
   * @return a map of event sources to register
   */
  Map<String, ? extends EventSource> prepareEventSources(EventSourceContext<P> context);

}
