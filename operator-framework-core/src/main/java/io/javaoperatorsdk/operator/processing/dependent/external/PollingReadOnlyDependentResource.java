package io.javaoperatorsdk.operator.processing.dependent.external;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceConfigurator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.api.reconciler.dependent.ReadOnlyDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PollingEventSource;

import static io.javaoperatorsdk.operator.processing.dependent.external.PollingDependentResourceConfig.DEFAULT_POLLING_PERIOD;

public abstract class PollingReadOnlyDependentResource<R, P extends HasMetadata, C extends PollingDependentResourceConfig>
    implements ReadOnlyDependentResource<R, P>, EventSourceProvider<P>,
    Supplier<Map<ResourceID, R>>, DependentResourceConfigurator<C> {

  private PollingEventSource<R, P> pollingEventSource;
  private long pollingPeriod = DEFAULT_POLLING_PERIOD;

  @Override
  public EventSource eventSource(EventSourceContext<P> context) {
    pollingEventSource = new PollingEventSource<>(this, pollingPeriod, resourceType());
    return null;
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return pollingEventSource.getAssociated(primaryResource);
  }

  protected Class<R> resourceType() {
    return (Class<R>) Utils.getFirstTypeArgumentFromExtendedClass(getClass());
  }

  @Override
  public void configureWith(C config) {
    this.pollingPeriod = config.getPollingPeriod();
  }
}
