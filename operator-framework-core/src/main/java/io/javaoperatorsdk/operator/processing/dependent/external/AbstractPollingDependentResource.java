package io.javaoperatorsdk.operator.processing.dependent.external;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Ignore;
import io.javaoperatorsdk.operator.processing.dependent.AbstractEventSourceHolderDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.CacheKeyMapper;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;

@Ignore
public abstract class AbstractPollingDependentResource<R, P extends HasMetadata>
    extends
    AbstractEventSourceHolderDependentResource<R, P, ExternalResourceCachingEventSource<R, P>>
    implements CacheKeyMapper<R> {

  public static final int DEFAULT_POLLING_PERIOD = 5000;
  private long pollingPeriod;

  protected AbstractPollingDependentResource(Class<R> resourceType) {
    this(resourceType, DEFAULT_POLLING_PERIOD);
  }

  public AbstractPollingDependentResource(Class<R> resourceType, long pollingPeriod) {
    super(resourceType);
    this.pollingPeriod = pollingPeriod;
  }

  public void setPollingPeriod(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
  }

  public long getPollingPeriod() {
    return pollingPeriod;
  }

  // for now dependent resources support event sources only with one owned resource.
  @Override
  public String keyFor(R resource) {
    return CacheKeyMapper.singleResourceCacheKeyMapper().keyFor(resource);
  }
}
