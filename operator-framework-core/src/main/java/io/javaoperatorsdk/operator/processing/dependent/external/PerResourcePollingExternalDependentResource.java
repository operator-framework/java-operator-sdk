package io.javaoperatorsdk.operator.processing.dependent.external;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;

import static io.javaoperatorsdk.operator.processing.dependent.external.PollingExternalDependentResource.DEFAULT_POLLING_PERIOD;

public abstract class PerResourcePollingExternalDependentResource<R, P extends HasMetadata>
    extends AbstractExternalCachingDependentResource<R, P>
    implements PerResourcePollingEventSource.ResourceSupplier<R, P> {

  protected long pollingPeriod;

  public PerResourcePollingExternalDependentResource() {
    this(DEFAULT_POLLING_PERIOD);
  }

  public PerResourcePollingExternalDependentResource(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
  }

  @Override
  public EventSource initEventSource(EventSourceContext<P> context) {
    eventSource = new PerResourcePollingEventSource<>(this, context.getPrimaryCache(),
        pollingPeriod, resourceType());
    return eventSource;
  }

  public PerResourcePollingExternalDependentResource<R, P> setPollingPeriod(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
    return this;
  }

  public long getPollingPeriod() {
    return pollingPeriod;
  }

}
