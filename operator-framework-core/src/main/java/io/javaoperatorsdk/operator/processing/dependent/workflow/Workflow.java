package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public interface Workflow<P extends HasMetadata> {

  boolean THROW_EXCEPTION_AUTOMATICALLY_DEFAULT = true;

  default WorkflowReconcileResult reconcile(P primary, Context<P> context) {
    throw new UnsupportedOperationException("Implement this");
  }

  default WorkflowCleanupResult cleanup(P primary, Context<P> context) {
    throw new UnsupportedOperationException("Implement this");
  }

  @SuppressWarnings("rawtypes")
  default Set<DependentResourceNode> getTopLevelDependentResources() {
    return Collections.emptySet();
  }

  @SuppressWarnings("rawtypes")
  default Set<DependentResourceNode> getBottomLevelResource() {
    return Collections.emptySet();
  }

  default boolean hasCleaner() {
    return false;
  }

  default boolean isEmpty() {
    return size() == 0;
  }

  default int size() {
    return getDependentResourcesByName().size();
  }

  @SuppressWarnings("rawtypes")
  default Map<String, DependentResource> getDependentResourcesByName() {
    return Collections.emptyMap();
  }

  @SuppressWarnings("rawtypes")
  default List<DependentResource> getDependentResourcesWithoutActivationCondition() {
    return Collections.emptyList();
  }
}
