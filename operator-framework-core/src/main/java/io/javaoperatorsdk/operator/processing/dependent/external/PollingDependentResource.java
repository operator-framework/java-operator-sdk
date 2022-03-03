package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Map;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

public abstract class PollingDependentResource<R, P extends HasMetadata>
    extends AbstractCachingDependentResource<R, P> implements Supplier<Map<ResourceID, R>> {

  public static final int DEFAULT_POLLING_PERIOD = 5000;
  protected long pollingPeriod;

  public PollingDependentResource() {
    this(DEFAULT_POLLING_PERIOD);
  }

  public PollingDependentResource(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
  }

  @Override
  public EventSource initEventSource(EventSourceContext<P> context) {
    eventSource = new PollingEventSource<>(this, pollingPeriod, resourceType());
    return eventSource;
  }

  public PollingDependentResource<R, P> setPollingPeriod(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
    return this;
  }
}
