package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import java.util.Collections;
import java.util.List;

public interface ManagedWorkflow<P extends HasMetadata> {

  @SuppressWarnings({"unused", "rawtypes"})
  default List<DependentResourceSpec> getOrderedSpecs() {
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
