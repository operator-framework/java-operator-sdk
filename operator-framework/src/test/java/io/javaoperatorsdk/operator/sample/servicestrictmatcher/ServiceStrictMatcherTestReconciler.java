package io.javaoperatorsdk.operator.sample.servicestrictmatcher;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import java.util.concurrent.atomic.AtomicInteger;

@Workflow(dependents = {@Dependent(type = ServiceDependentResource.class)})
@ControllerConfiguration
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
