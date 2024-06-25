package io.javaoperatorsdk.operator.sample.dependentoperationeventfiltering;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.InformerConfig;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(dependents = {
    @Dependent(type = ConfigMapDependentResource.class)
})
@ControllerConfiguration(
    informerConfig = @InformerConfig(namespaces = Constants.WATCH_CURRENT_NAMESPACE))
public class DependentOperationEventFilterCustomResourceTestReconciler
    implements Reconciler<DependentOperationEventFilterCustomResource>,
    TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<DependentOperationEventFilterCustomResource> reconcile(
      DependentOperationEventFilterCustomResource resource,
      Context<DependentOperationEventFilterCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
