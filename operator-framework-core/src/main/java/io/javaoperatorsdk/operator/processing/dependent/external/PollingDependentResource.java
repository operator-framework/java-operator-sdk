package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Map;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

public abstract class PollingDependentResource<R, P extends HasMetadata>
    extends AbstractPollingDependentResource<R, P> implements Supplier<Map<ResourceID, R>> {

  public PollingDependentResource(Class<R> resourceType) {
    super(resourceType);
  }

  public PollingDependentResource(Class<R> resourceType, long pollingPeriod) {
    super(resourceType, pollingPeriod);
  }

  @Override
  public EventSource initEventSource(EventSourceContext<P> context) {
    eventSource = new PollingEventSource<>(this, getPollingPeriod(), resourceType());
    return eventSource;
  }
}
