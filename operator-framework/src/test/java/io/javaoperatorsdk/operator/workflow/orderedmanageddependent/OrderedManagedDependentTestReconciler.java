package io.javaoperatorsdk.operator.workflow.orderedmanageddependent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(
    dependents = {
      @Dependent(type = ConfigMapDependentResource1.class, name = "cm1"),
      @Dependent(type = ConfigMapDependentResource2.class, dependsOn = "cm1")
    })
@ControllerConfiguration(informer = @Informer(namespaces = Constants.WATCH_CURRENT_NAMESPACE))
public class OrderedManagedDependentTestReconciler
    implements Reconciler<OrderedManagedDependentCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  public static final List<Class<?>> dependentExecution =
      Collections.synchronizedList(new ArrayList<>());

  @Override
  public UpdateControl<OrderedManagedDependentCustomResource> reconcile(
      OrderedManagedDependentCustomResource resource,
      Context<OrderedManagedDependentCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
