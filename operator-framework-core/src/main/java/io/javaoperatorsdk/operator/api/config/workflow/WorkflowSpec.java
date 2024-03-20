package io.javaoperatorsdk.operator.api.config.workflow;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import java.util.List;

public interface WorkflowSpec {

  @SuppressWarnings("rawtypes")
  List<DependentResourceSpec> getDependentResourceSpecs();

  boolean isExplicitInvocation();
}
