package io.javaoperatorsdk.operator.processing.dependent.external;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

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
  public EventSource initEventSource(EventSourceContext<P> context) {
    eventSource = new PerResourcePollingEventSource<>(this, context.getPrimaryCache(),
        getPollingPeriod(), resourceType());
    return eventSource;
  }
}
