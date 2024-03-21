package io.javaoperatorsdk.operator.sample.informerrelatedbehavior;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(dependents = @Dependent(
    name = InformerRelatedBehaviorTestReconciler.CONFIG_MAP_DEPENDENT_RESOURCE,
    type = ConfigMapDependentResource.class))
@ControllerConfiguration(
    name = InformerRelatedBehaviorTestReconciler.INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER)
public class InformerRelatedBehaviorTestReconciler
    implements Reconciler<InformerRelatedBehaviorTestCustomResource>, TestExecutionInfoProvider {

  private static final Logger log =
      LoggerFactory.getLogger(InformerRelatedBehaviorTestReconciler.class);

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
    log.info("Reconciled for: {}", ResourceID.fromResource(resource));
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
