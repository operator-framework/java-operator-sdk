package io.javaoperatorsdk.operator.api.config.workflow;

import java.util.List;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

public class WorkflowSpec {

  @SuppressWarnings("rawtypes")
  private final List<DependentResourceSpec> dependentResourceSpecs;
  private final boolean explicitInvocation;

  public WorkflowSpec(List<DependentResourceSpec> dependentResourceSpecs,
      boolean explicitInvocation) {
    this.dependentResourceSpecs = dependentResourceSpecs;
    this.explicitInvocation = explicitInvocation;
  }

  public List<DependentResourceSpec> getDependentResourceSpecs() {
    return dependentResourceSpecs;
  }

  public boolean isExplicitInvocation() {
    return explicitInvocation;
  }
}
