package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.IDMapper;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

public abstract class PollingDependentResource<R, P extends HasMetadata>
    extends AbstractPollingDependentResource<R, P> implements Supplier<Map<ResourceID, Set<R>>> {

  private final IDMapper<R> idProvider;

  public PollingDependentResource(Class<R> resourceType, IDMapper<R> idProvider) {
    super(resourceType);
    this.idProvider = idProvider;
  }

  public PollingDependentResource(Class<R> resourceType, long pollingPeriod, IDMapper<R> idProvider) {
    super(resourceType, pollingPeriod);
    this.idProvider = idProvider;
  }

  @Override
  protected ExternalResourceCachingEventSource<R, P> createEventSource(
      EventSourceContext<P> context) {
    return new PollingEventSource<>(this, getPollingPeriod(), resourceType(), idProvider);
  }

}
