package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigService;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.EMPTY_STRING;
import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT;

public interface KubernetesDependentResourceConfigService extends DependentResourceConfigService {

  default boolean addOwnerReference() {
    return ADD_OWNER_REFERENCE_DEFAULT;
  };

  default String[] namespaces() {
    return new String[0];
  }

  default String labelSelector() {
    return EMPTY_STRING;
  };

}
