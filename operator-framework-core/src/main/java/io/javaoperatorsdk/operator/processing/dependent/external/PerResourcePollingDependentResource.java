package io.javaoperatorsdk.operator.processing.dependent.external;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

import static io.javaoperatorsdk.operator.processing.dependent.external.PollingDependentResource.DEFAULT_POLLING_PERIOD;

public abstract class PerResourcePollingDependentResource<R, P extends HasMetadata>
    extends AbstractCachingDependentResource<R, P>
    implements PerResourcePollingEventSource.ResourceSupplier<R, P> {

  protected long pollingPeriod;

  public PerResourcePollingDependentResource() {
    this(DEFAULT_POLLING_PERIOD);
  }

  public PerResourcePollingDependentResource(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
  }

  @Override
  public EventSource initEventSource(EventSourceContext<P> context) {
    eventSource = new PerResourcePollingEventSource<>(this, context.getPrimaryCache(),
        pollingPeriod, resourceType());
    return eventSource;
  }

  public PerResourcePollingDependentResource<R, P> setPollingPeriod(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
    return this;
  }

  public long getPollingPeriod() {
    return pollingPeriod;
  }

}
