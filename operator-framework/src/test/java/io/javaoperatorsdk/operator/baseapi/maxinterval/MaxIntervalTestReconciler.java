package io.javaoperatorsdk.operator.baseapi.maxinterval;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.MaxReconciliationInterval;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(
    maxReconciliationInterval =
        @MaxReconciliationInterval(interval = 50, timeUnit = TimeUnit.MILLISECONDS))
public class MaxIntervalTestReconciler
    implements Reconciler<MaxIntervalTestCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<MaxIntervalTestCustomResource> reconcile(
      MaxIntervalTestCustomResource resource, Context<MaxIntervalTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
