package io.javaoperatorsdk.operator.processing.dependent.external;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.processing.dependent.AbstractExternalDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;

@Ignore
public abstract class AbstractPollingDependentResource<R, P extends HasMetadata>
    extends AbstractExternalDependentResource<R, P, ExternalResourceCachingEventSource<R, P>>
    implements CacheKeyMapper<R> {

  public static final Duration DEFAULT_POLLING_PERIOD = Duration.ofMillis(5000);
  private Duration pollingPeriod;

  protected AbstractPollingDependentResource() {}

  protected AbstractPollingDependentResource(Class<R> resourceType) {
    this(resourceType, DEFAULT_POLLING_PERIOD);
  }

  public AbstractPollingDependentResource(Class<R> resourceType, Duration pollingPeriod) {
    super(resourceType);
    this.pollingPeriod = pollingPeriod;
  }

  public void setPollingPeriod(Duration pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
  }

  public Duration getPollingPeriod() {
    return pollingPeriod;
  }

  // for now dependent resources support event sources only with one owned resource.
  @Override
  public String keyFor(R resource) {
    return CacheKeyMapper.singleResourceCacheKeyMapper().keyFor(resource);
  }
}
