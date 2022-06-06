package io.javaoperatorsdk.operator.sample.workflowallfeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ConfigMapDeletePostCondition
    implements Condition<ConfigMap, WorkflowAllFeatureCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapDeletePostCondition.class);

  @Override
  public boolean isMet(
      DependentResource<ConfigMap, WorkflowAllFeatureCustomResource> dependentResource,
      WorkflowAllFeatureCustomResource primary, Context<WorkflowAllFeatureCustomResource> context) {
    var configMapDeleted = dependentResource.getSecondaryResource(primary).isEmpty();
    log.debug("Config Map Deleted: {}", configMapDeleted);
    return configMapDeleted;
  }
}
