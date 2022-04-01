package io.javaoperatorsdk.operator.sample.orderedmanageddependent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(dependents = {@Dependent(type = ConfigMapDependentResource1.class),
    @Dependent(type = ConfigMapDependentResource2.class)})
public class OrderedManagedDependentTestReconciler
    implements Reconciler<OrderedManagedDependentCustomResource>,
    TestExecutionInfoProvider {

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
