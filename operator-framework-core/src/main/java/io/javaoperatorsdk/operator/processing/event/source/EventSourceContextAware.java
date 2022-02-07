package io.javaoperatorsdk.operator.processing.event.source;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;

// todo do we really need this
public interface EventSourceContextAware<P extends HasMetadata> {
  void initWith(EventSourceContext<P> context);
}
