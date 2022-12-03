package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Collections;
import java.util.List;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;

public interface ManagedWorkflow<P extends HasMetadata> {

  @SuppressWarnings("unused")
  default List<DependentResourceSpec<?, ?>> getOrderedSpecs() {
    return Collections.emptyList();
  }

  default boolean hasCleaner() {
    return false;
  }

  default boolean isEmpty() {
    return true;
  }

  Workflow<P> resolve(KubernetesClient client, ControllerConfiguration<P> configuration);
}
