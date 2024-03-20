package io.javaoperatorsdk.operator.sample.dependentdifferentnamespace;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;
import java.util.concurrent.atomic.AtomicInteger;

@Workflow(dependents = {
    @Dependent(type = ConfigMapDependentResource.class),
})
@ControllerConfiguration
public class DependentDifferentNamespaceReconciler
    implements Reconciler<DependentDifferentNamespaceCustomResource>,
    TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<DependentDifferentNamespaceCustomResource> reconcile(
      DependentDifferentNamespaceCustomResource resource,
      Context<DependentDifferentNamespaceCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
