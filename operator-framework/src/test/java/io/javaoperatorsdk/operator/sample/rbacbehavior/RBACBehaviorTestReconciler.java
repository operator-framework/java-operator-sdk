package io.javaoperatorsdk.operator.sample.rbacbehavior;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(dependents = @Dependent(type = ConfigMapDependentResource.class))
public class RBACBehaviorTestReconciler
    implements Reconciler<RBACBehaviorTestCustomResource>, TestExecutionInfoProvider {


  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient client;

  @Override
  public UpdateControl<RBACBehaviorTestCustomResource> reconcile(
      RBACBehaviorTestCustomResource resource,
      Context<RBACBehaviorTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
