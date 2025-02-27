package io.javaoperatorsdk.operator.workflow.crdpresentactivation;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.CRDPresentActivationCondition;

@Workflow(
    dependents = {
      @Dependent(
          type = CRDPresentActivationDependent.class,
          activationCondition = CRDPresentActivationCondition.class),
    })
// to trigger reconciliation with metadata change
@ControllerConfiguration(generationAwareEventProcessing = false)
public class CRDPresentActivationReconciler
    implements Reconciler<CRDPresentActivationCustomResource>,
        Cleaner<CRDPresentActivationCustomResource> {

  @Override
  public UpdateControl<CRDPresentActivationCustomResource> reconcile(
      CRDPresentActivationCustomResource resource,
      Context<CRDPresentActivationCustomResource> context) {

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(
      CRDPresentActivationCustomResource resource,
      Context<CRDPresentActivationCustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
