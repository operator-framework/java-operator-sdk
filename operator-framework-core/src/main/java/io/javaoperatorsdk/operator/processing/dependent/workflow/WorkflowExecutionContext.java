package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface WorkflowExecutionContext {

  List<DependentResource> getDependentResources();

}
