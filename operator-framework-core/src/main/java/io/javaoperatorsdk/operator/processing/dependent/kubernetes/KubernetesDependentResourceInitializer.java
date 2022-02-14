package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceInitializer;

import static io.javaoperatorsdk.operator.api.config.Utils.valueOrDefault;

public class KubernetesDependentResourceInitializer<T extends DependentResource<?, ?>>
    implements DependentResourceInitializer<T> {

  @Override
  @SuppressWarnings("unchecked")
  public T initialize(
      Class<T> dependentResourceClass,
      ControllerConfiguration<?> controllerConfiguration,
      KubernetesClient client) {
    try {
      KubernetesDependentResource<?, ?> kubernetesDependentResource =
          (KubernetesDependentResource<?, ?>) dependentResourceClass.getConstructor().newInstance();
      kubernetesDependentResource.setKubernetesClient(client);

      final var kubeDependent = dependentResourceClass.getAnnotation(KubernetesDependent.class);
      final var namespaces =
          valueOrDefault(
              kubeDependent,
              KubernetesDependent::namespaces,
              controllerConfiguration.getNamespaces().toArray(new String[0]));
      final var labelSelector =
          valueOrDefault(kubeDependent, KubernetesDependent::labelSelector, null);
      final var owned =
          valueOrDefault(
              kubeDependent, KubernetesDependent::addOwnerReference,
              KubernetesDependent.OWNED_DEFAULT);

      kubernetesDependentResource.configureWith(controllerConfiguration.getConfigurationService(),
          labelSelector,
          Set.of(namespaces), owned);
      return (T) kubernetesDependentResource;
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }
}
