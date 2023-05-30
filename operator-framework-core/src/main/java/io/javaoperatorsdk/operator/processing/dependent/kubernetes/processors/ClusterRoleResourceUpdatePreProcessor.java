package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.rbac.ClusterRole;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ClusterRoleResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<ClusterRole> {

  @Override
  protected void updateClonedActual(ClusterRole actual, ClusterRole desired) {
    actual.setAggregationRule(desired.getAggregationRule());
    actual.setRules(desired.getRules());
  }

  @Override
  public boolean matches(ClusterRole actual, ClusterRole desired, boolean equality,
      ObjectMapper objectMapper,
      String[] ignoredPaths) {
    return Objects.equals(actual.getRules(), desired.getRules()) &&
        Objects.equals(actual.getAggregationRule(), desired.getAggregationRule());
  }
}
