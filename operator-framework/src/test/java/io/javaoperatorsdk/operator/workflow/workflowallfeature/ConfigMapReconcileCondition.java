package io.javaoperatorsdk.operator.workflow.workflowallfeature;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.DetailedCondition;

public class ConfigMapReconcileCondition
    implements DetailedCondition<ConfigMap, WorkflowAllFeatureCustomResource, String> {

  public static final String CREATE_SET = "create set";
  public static final String CREATE_NOT_SET = "create not set";
  public static final String NOT_RECONCILED_YET = "Not reconciled yet";

  @Override
  public Result<String> detailedIsMet(
      DependentResource<ConfigMap, WorkflowAllFeatureCustomResource> dependentResource,
      WorkflowAllFeatureCustomResource primary,
      Context<WorkflowAllFeatureCustomResource> context) {
    final var createConfigMap = primary.getSpec().isCreateConfigMap();
    return Result.withResult(createConfigMap, createConfigMap ? CREATE_SET : CREATE_NOT_SET);
  }
}
