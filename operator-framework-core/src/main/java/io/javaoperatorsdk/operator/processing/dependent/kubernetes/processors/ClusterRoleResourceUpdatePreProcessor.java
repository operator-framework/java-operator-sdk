package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.rbac.ClusterRole;

public class ClusterRoleResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<ClusterRole> {

  @Override
  protected void updateClonedActual(ClusterRole actual, ClusterRole desired) {
    actual.setAggregationRule(desired.getAggregationRule());
    actual.setRules(desired.getRules());
  }
}
