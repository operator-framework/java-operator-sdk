package io.javaoperatorsdk.operator.baseapi.unmodifiabledependentpart;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {@Dependent(type = UnmodifiablePartConfigMapDependent.class)})
@ControllerConfiguration
public class UnmodifiableDependentPartReconciler
    implements Reconciler<UnmodifiableDependentPartCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<UnmodifiableDependentPartCustomResource> reconcile(
      UnmodifiableDependentPartCustomResource resource,
      Context<UnmodifiableDependentPartCustomResource> context)
      throws InterruptedException {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
