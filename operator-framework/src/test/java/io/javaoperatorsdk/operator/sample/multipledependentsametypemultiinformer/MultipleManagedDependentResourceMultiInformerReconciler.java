package io.javaoperatorsdk.operator.sample.multipledependentsametypemultiinformer;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(dependents = {
    @Dependent(name = MultipleManagedDependentResourceMultiInformerReconciler.CONFIG_MAP_1_DR,
        type = MultipleManagedDependentResourceMultiInformerConfigMap1.class),
    @Dependent(name = MultipleManagedDependentResourceMultiInformerReconciler.CONFIG_MAP_2_DR,
        type = MultipleManagedDependentResourceMultiInformerConfigMap2.class)
})
public class MultipleManagedDependentResourceMultiInformerReconciler
    implements Reconciler<MultipleManagedDependentResourceMultiInformerCustomResource>,
    TestExecutionInfoProvider {

  public static final String CONFIG_MAP_EVENT_SOURCE = "ConfigMapEventSource";
  public static final String DATA_KEY = "key";
  public static final String CONFIG_MAP_1_DR = "ConfigMap1";
  public static final String CONFIG_MAP_2_DR = "ConfigMap2";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  public MultipleManagedDependentResourceMultiInformerReconciler() {}

  @Override
  public UpdateControl<MultipleManagedDependentResourceMultiInformerCustomResource> reconcile(
      MultipleManagedDependentResourceMultiInformerCustomResource resource,
      Context<MultipleManagedDependentResourceMultiInformerCustomResource> context) {
    numberOfExecutions.getAndIncrement();

    return UpdateControl.noUpdate();
  }


  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
