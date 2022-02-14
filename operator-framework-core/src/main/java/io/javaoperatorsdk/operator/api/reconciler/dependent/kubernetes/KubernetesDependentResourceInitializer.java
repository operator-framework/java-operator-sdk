package io.javaoperatorsdk.operator.api.reconciler.dependent.kubernetes;

import java.lang.reflect.InvocationTargetException;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceInitializer;

import static io.javaoperatorsdk.operator.api.config.Utils.valueOrDefault;

public class KubernetesDependentResourceInitializer<T extends DependentResource<?, ?>>
    implements DependentResourceInitializer<T> {

  @Override
  @SuppressWarnings("unchecked")
  public T initialize(
      Class<T> dependentResourceClass,
      ControllerConfiguration<?> configurationService,
      KubernetesClient client) {
    try {
      KubernetesDependentResource<?, ?> kubernetesDependentResource =
          (KubernetesDependentResource<?, ?>) dependentResourceClass.getConstructor().newInstance();
      kubernetesDependentResource.setKubernetesClient(client);
      KubernetesDependentResourceConfiguration config =
          loadConfiguration(
              dependentResourceClass,
              configurationService,
              kubernetesDependentResource.resourceType());
      kubernetesDependentResource.configureWith(config);
      return (T) kubernetesDependentResource;
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  private KubernetesDependentResourceConfiguration loadConfiguration(
      Class<T> dependentResourceClass,
      ControllerConfiguration<?> controllerConfiguration,
      Class<?> resourceType) {

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
            kubeDependent, KubernetesDependent::owned, KubernetesDependent.OWNED_DEFAULT);

    final var configuration =
        InformerConfiguration.from(controllerConfiguration.getConfigurationService(), resourceType)
            .withLabelSelector(labelSelector)
            .withNamespaces(namespaces)
            .build();

    return KubernetesDependentResourceConfiguration.from(configuration, owned,
        dependentResourceClass);
  }
}
