package io.javaoperatorsdk.operator.dependent.ssalegacymatcher;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {@Dependent(type = ServiceDependentResource.class)})
@ControllerConfiguration
public class SSALegacyMatcherReconciler implements Reconciler<SSALegacyMatcherCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<SSALegacyMatcherCustomResource> reconcile(
      SSALegacyMatcherCustomResource resource, Context<SSALegacyMatcherCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
