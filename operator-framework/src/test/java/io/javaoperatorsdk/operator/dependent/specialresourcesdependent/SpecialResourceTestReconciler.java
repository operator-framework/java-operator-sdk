package io.javaoperatorsdk.operator.dependent.specialresourcesdependent;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(
    dependents = {
      @Dependent(type = ServiceAccountDependentResource.class),
    })
@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE))
public class SpecialResourceTestReconciler
    implements Reconciler<SpecialResourceCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<SpecialResourceCustomResource> reconcile(
      SpecialResourceCustomResource resource, Context<SpecialResourceCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
