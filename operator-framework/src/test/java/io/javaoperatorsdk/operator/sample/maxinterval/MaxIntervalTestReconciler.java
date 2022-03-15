package io.javaoperatorsdk.operator.sample.maxinterval;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(reconciliationMaxInterval = @ReconciliationMaxInterval(interval = 50,
    timeUnit = TimeUnit.MILLISECONDS))
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
