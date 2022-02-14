package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public interface DependentResource<R, P extends HasMetadata, C extends DependentResourceConfiguration<R, P>> {

  Optional<EventSource> eventSource(EventSourceContext<P> context);

  @SuppressWarnings("unchecked")
  default Class<R> resourceType() {
    return (Class<R>) Utils.getFirstTypeArgumentFromInterface(getClass());
  }

  void reconcile(P primary, Context context);

  void delete(P primary, Context context);

  Optional<R> getResource(P primaryResource);

  default void configureWith(C config) {}
}
