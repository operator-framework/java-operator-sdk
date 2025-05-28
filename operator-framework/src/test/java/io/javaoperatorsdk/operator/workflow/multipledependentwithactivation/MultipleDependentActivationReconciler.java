package io.javaoperatorsdk.operator.workflow.multipledependentwithactivation;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(
    dependents = {
      @Dependent(
          type = ConfigMapDependentResource1.class,
          activationCondition = ActivationCondition.class),
      @Dependent(
          type = ConfigMapDependentResource2.class,
          activationCondition = ActivationCondition.class),
      @Dependent(type = SecretDependentResource.class)
    })
@ControllerConfiguration
public class MultipleDependentActivationReconciler
    implements Reconciler<MultipleDependentActivationCustomResource> {

  private final AtomicInteger numberOfReconciliationExecution = new AtomicInteger(0);

  @Override
  public UpdateControl<MultipleDependentActivationCustomResource> reconcile(
      MultipleDependentActivationCustomResource resource,
      Context<MultipleDependentActivationCustomResource> context) {

    numberOfReconciliationExecution.incrementAndGet();

    return UpdateControl.noUpdate();
  }

  public int getNumberOfReconciliationExecution() {
    return numberOfReconciliationExecution.get();
  }
}
