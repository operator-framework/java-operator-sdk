package io.javaoperatorsdk.operator.baseapi.ratelimit;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimited;

@RateLimited(
    maxReconciliations = 1,
    within = RateLimitReconciler.REFRESH_PERIOD,
    unit = TimeUnit.MILLISECONDS)
@ControllerConfiguration
public class RateLimitReconciler implements Reconciler<RateLimitCustomResource> {

  public static final int REFRESH_PERIOD = 3000;

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<RateLimitCustomResource> reconcile(
      RateLimitCustomResource resource, Context<RateLimitCustomResource> context) {

    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
