package io.javaoperatorsdk.operator.api.config.workflow;

import java.util.List;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

public interface WorkflowSpec {

  @SuppressWarnings("rawtypes")
  List<DependentResourceSpec> getDependentResourceSpecs();

  boolean isExplicitInvocation();
}
