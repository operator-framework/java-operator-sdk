package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

/**
 * @deprecated now event source related methods are directly on {@link DependentResource}
 * @param <P> primary resource
 */
@Deprecated(forRemoval = true)
public interface EventSourceProvider<P extends HasMetadata> {
  /**
   * @param context - event source context where the event source is initialized
   * @return the initiated event source.
   */
  EventSource initEventSource(EventSourceContext<P> context);
}
