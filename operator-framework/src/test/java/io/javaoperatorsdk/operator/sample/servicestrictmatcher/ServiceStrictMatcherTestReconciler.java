package io.javaoperatorsdk.operator.sample.servicestrictmatcher;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.workflow.Workflow;

@ControllerConfiguration(
    workflow = @Workflow(dependents = {@Dependent(type = ServiceDependentResource.class)}))
public class ServiceStrictMatcherTestReconciler
    implements Reconciler<ServiceStrictMatcherTestCustomResource> {


  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<ServiceStrictMatcherTestCustomResource> reconcile(
      ServiceStrictMatcherTestCustomResource resource,
      Context<ServiceStrictMatcherTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
