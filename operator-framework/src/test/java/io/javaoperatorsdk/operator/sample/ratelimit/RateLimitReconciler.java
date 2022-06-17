package io.javaoperatorsdk.operator.sample.ratelimit;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;

@ControllerConfiguration(rateLimit = @RateLimit(limitForPeriod = 1,
    refreshPeriod = RateLimitReconciler.REFRESH_PERIOD,
    refreshPeriodTimeUnit = TimeUnit.MILLISECONDS))
public class RateLimitReconciler
    implements Reconciler<RateLimitCustomResource> {

  public static final int REFRESH_PERIOD = 3000;

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<RateLimitCustomResource> reconcile(
      RateLimitCustomResource resource,
      Context<RateLimitCustomResource> context) {

    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
