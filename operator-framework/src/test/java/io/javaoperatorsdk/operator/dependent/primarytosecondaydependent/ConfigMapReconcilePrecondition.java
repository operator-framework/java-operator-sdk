package io.javaoperatorsdk.operator.dependent.primarytosecondaydependent;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ConfigMapReconcilePrecondition
    implements Condition<ConfigMap, PrimaryToSecondaryDependentCustomResource> {

  public static final String DO_NOT_RECONCILE = "doNotReconcile";

  @Override
  public boolean isMet(
      DependentResource<ConfigMap, PrimaryToSecondaryDependentCustomResource> dependentResource,
      PrimaryToSecondaryDependentCustomResource primary,
      Context<PrimaryToSecondaryDependentCustomResource> context) {
    return dependentResource
        .getSecondaryResource(primary, context)
        .map(
            cm -> {
              var data = cm.getData().get(PrimaryToSecondaryDependentReconciler.DATA_KEY);
              return data != null && !data.equals(DO_NOT_RECONCILE);
            })
        .orElse(false);
  }
}
