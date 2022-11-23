package io.javaoperatorsdk.operator.sample.informerrelatedbehavior;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration(
    name = InformerRelatedBehaviorTestReconciler.INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER,
    dependents = @Dependent(
        name = InformerRelatedBehaviorTestReconciler.CONFIG_MAP_DEPENDENT_RESOURCE,
        type = ConfigMapDependentResource.class))
public class InformerRelatedBehaviorTestReconciler
    implements Reconciler<InformerRelatedBehaviorTestCustomResource>, TestExecutionInfoProvider {

  public static final String INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER =
      "InformerRelatedBehaviorTestReconciler";
  public static final String CONFIG_MAP_DEPENDENT_RESOURCE = "ConfigMapDependentResource";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private KubernetesClient client;

  @Override
  public UpdateControl<InformerRelatedBehaviorTestCustomResource> reconcile(
      InformerRelatedBehaviorTestCustomResource resource,
      Context<InformerRelatedBehaviorTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
