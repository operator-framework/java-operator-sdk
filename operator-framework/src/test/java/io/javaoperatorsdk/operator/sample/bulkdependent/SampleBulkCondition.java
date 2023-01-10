package io.javaoperatorsdk.operator.sample.bulkdependent;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class SampleBulkCondition
    implements Condition<Map<String, ConfigMap>, BulkDependentTestCustomResource> {

  // We use ConfigMaps here just to show how to check some properties of resources managed by a
  // BulkDependentResource. In real life example this would be rather based on some status of those
  // resources, like Pods.

  @Override
  public boolean isMet(BulkDependentTestCustomResource primary, Map<String, ConfigMap> secondary,
      Context<BulkDependentTestCustomResource> context) {
    return secondary.values().stream().noneMatch(cm -> cm.getData().isEmpty());
  }
}
