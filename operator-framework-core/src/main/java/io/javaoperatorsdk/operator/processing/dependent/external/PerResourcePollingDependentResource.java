package io.javaoperatorsdk.operator.processing.dependent.external;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingConfigurationBuilder;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

@Ignore
public abstract class PerResourcePollingDependentResource<R, P extends HasMetadata>
    extends AbstractPollingDependentResource<R, P>
    implements PerResourcePollingEventSource.ResourceFetcher<R, P> {

  public PerResourcePollingDependentResource() {}

  public PerResourcePollingDependentResource(Class<R> resourceType) {
    super(resourceType);
  }

  public PerResourcePollingDependentResource(Class<R> resourceType, Duration pollingPeriod) {
    super(resourceType, pollingPeriod);
  }

  @Override
  protected ExternalResourceCachingEventSource<R, P> createEventSource(
      EventSourceContext<P> context) {

    return new PerResourcePollingEventSource<>(
        resourceType(),
        context,
        new PerResourcePollingConfigurationBuilder<>(this, getPollingPeriod())
            .withCacheKeyMapper(this)
            .withName(name())
            .build());
  }
}
