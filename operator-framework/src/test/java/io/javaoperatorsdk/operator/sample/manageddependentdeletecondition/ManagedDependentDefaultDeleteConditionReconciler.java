package io.javaoperatorsdk.operator.sample.manageddependentdeletecondition;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.workflow.KubernetesResourceDeletedCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Workflow(dependents = {
    @Dependent(name = "ConfigMap", type = ConfigMapDependent.class),
    @Dependent(type = SecretDependent.class, dependsOn = "ConfigMap",
        deletePostcondition = KubernetesResourceDeletedCondition.class)
})
@ControllerConfiguration
public class ManagedDependentDefaultDeleteConditionReconciler
    implements Reconciler<ManagedDependentDefaultDeleteConditionCustomResource> {

  private static final Logger log =
      LoggerFactory.getLogger(ManagedDependentDefaultDeleteConditionReconciler.class);

  @Override
  public UpdateControl<ManagedDependentDefaultDeleteConditionCustomResource> reconcile(
      ManagedDependentDefaultDeleteConditionCustomResource resource,
      Context<ManagedDependentDefaultDeleteConditionCustomResource> context) {

    log.debug("Reconciled: {}", resource);

    return UpdateControl.noUpdate();
  }

}
