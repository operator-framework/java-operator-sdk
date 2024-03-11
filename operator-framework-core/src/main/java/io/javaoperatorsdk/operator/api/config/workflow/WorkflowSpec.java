package io.javaoperatorsdk.operator.api.config.workflow;

import java.util.List;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

public class WorkflowSpec {

  @SuppressWarnings("rawtypes")
  private final List<DependentResourceSpec> dependentResourceSpecs;

  public WorkflowSpec(List<DependentResourceSpec> dependentResourceSpecs) {
    this.dependentResourceSpecs = dependentResourceSpecs;
  }

  public List<DependentResourceSpec> getDependentResourceSpecs() {
    return dependentResourceSpecs;
  }
}
