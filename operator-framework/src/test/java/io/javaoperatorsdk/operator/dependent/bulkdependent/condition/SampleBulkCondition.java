package io.javaoperatorsdk.operator.dependent.bulkdependent.condition;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.CRUDConfigMapBulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class SampleBulkCondition implements Condition<ConfigMap, BulkDependentTestCustomResource> {

  // We use ConfigMaps here just to show how to check some properties of resources managed by a
  // BulkDependentResource. In real life example this would be rather based on some status of those
  // resources, like Pods.

  @Override
  public boolean isMet(
      DependentResource<ConfigMap, BulkDependentTestCustomResource> dependentResource,
      BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {

    var resources =
        ((CRUDConfigMapBulkDependentResource) dependentResource)
            .getSecondaryResources(primary, context);
    return resources.values().stream().noneMatch(cm -> cm.getData().isEmpty());
  }
}
