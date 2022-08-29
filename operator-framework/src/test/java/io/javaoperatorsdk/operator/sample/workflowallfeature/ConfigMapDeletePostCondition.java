package io.javaoperatorsdk.operator.sample.workflowallfeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ConfigMapDeletePostCondition
    implements Condition<ConfigMap, WorkflowAllFeatureCustomResource> {

  private static final Logger log = LoggerFactory.getLogger(ConfigMapDeletePostCondition.class);

  @Override
  public boolean isMet(
      WorkflowAllFeatureCustomResource primary, ConfigMap secondary,
      Context<WorkflowAllFeatureCustomResource> context) {
    var configMapDeleted = secondary == null;
    log.debug("Config Map Deleted: {}", configMapDeleted);
    return configMapDeleted;
  }
}
