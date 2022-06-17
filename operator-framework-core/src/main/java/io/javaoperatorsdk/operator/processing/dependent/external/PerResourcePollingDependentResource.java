package io.javaoperatorsdk.operator.processing.dependent.external;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

@Ignore
public abstract class PerResourcePollingDependentResource<R, P extends HasMetadata>
    extends AbstractPollingDependentResource<R, P>
    implements PerResourcePollingEventSource.ResourceFetcher<R, P> {


  public PerResourcePollingDependentResource(Class<R> resourceType) {
    super(resourceType);
  }

  public PerResourcePollingDependentResource(Class<R> resourceType, long pollingPeriod) {
    super(resourceType, pollingPeriod);
  }

  @Override
  protected ExternalResourceCachingEventSource<R, P> createEventSource(
      EventSourceContext<P> context) {
    return new PerResourcePollingEventSource<>(this, context.getPrimaryCache(),
        getPollingPeriod(), resourceType(), this);
  }

}
