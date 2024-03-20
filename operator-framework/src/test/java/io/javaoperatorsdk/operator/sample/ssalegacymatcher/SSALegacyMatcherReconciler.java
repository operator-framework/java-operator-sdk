package io.javaoperatorsdk.operator.sample.ssalegacymatcher;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import java.util.concurrent.atomic.AtomicInteger;

@Workflow(dependents = {@Dependent(type = ServiceDependentResource.class)})
@ControllerConfiguration
public class SSALegacyMatcherReconciler
    implements Reconciler<SSALegacyMatcherCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<SSALegacyMatcherCustomResource> reconcile(
      SSALegacyMatcherCustomResource resource,
      Context<SSALegacyMatcherCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
