package io.javaoperatorsdk.operator.sample.primarytosecondarydependent;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class PrimaryToSecondaryDependentReconciler
    implements Reconciler<PrimaryToSecondaryDependentCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<PrimaryToSecondaryDependentCustomResource> reconcile(
      PrimaryToSecondaryDependentCustomResource resource,
      Context<PrimaryToSecondaryDependentCustomResource> context) {

    numberOfExecutions.incrementAndGet();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
