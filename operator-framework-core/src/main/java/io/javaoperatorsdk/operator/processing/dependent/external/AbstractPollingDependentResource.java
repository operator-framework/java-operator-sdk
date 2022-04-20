package io.javaoperatorsdk.operator.processing.dependent.external;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.IDMapper;

import java.util.function.Function;

public abstract class AbstractPollingDependentResource<R, P extends HasMetadata>
    extends AbstractCachingDependentResource<R, P> implements IDMapper<R> {

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
}
