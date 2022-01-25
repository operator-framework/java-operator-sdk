package io.javaoperatorsdk.experimental.dependentresource;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;

public abstract class DependentResource<D extends HasMetadata, P extends HasMetadata> {

  private final String name;

  public DependentResource(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public abstract ReconciliationResult reconcile(ReconciliationContext<P> reconciliationContext);

  public abstract Optional<D> getResource(P primaryResource);

  public abstract Optional<EventSource> eventSource();
}
