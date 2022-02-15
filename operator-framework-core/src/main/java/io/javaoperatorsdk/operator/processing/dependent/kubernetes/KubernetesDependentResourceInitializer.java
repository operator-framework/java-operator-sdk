package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResourceInitializer;

import static io.javaoperatorsdk.operator.api.config.Utils.valueOrDefault;

public class KubernetesDependentResourceInitializer<T extends DependentResource<?, ?>>
    implements DependentResourceInitializer<T, KubernetesDependentResourceConfigService> {

  private KubernetesDependentResourceConfigService configService;

  @Override
  public void useConfigService(KubernetesDependentResourceConfigService configService) {
    this.configService = configService;
  }

  @Override
  @SuppressWarnings("unchecked")
  public T initialize(
      Class<T> dependentResourceClass,
      ControllerConfiguration<?> controllerConfiguration,
      KubernetesClient client) {
    try {
      KubernetesDependentResource<?, ?> kubernetesDependentResource =
          (KubernetesDependentResource<?, ?>) dependentResourceClass
              .getConstructor(KubernetesClient.class)
              .newInstance(client);

      String[] namespaces;
      String labelSelector;
      boolean addOwnerReference;

      if (configService != null) {
        namespaces = configService.namespaces();
        labelSelector = configService.labelSelector();
        addOwnerReference = configService.addOwnerReference();
      } else {
        final var kubeDependent = dependentResourceClass.getAnnotation(KubernetesDependent.class);
        namespaces =
            valueOrDefault(
                kubeDependent,
                KubernetesDependent::namespaces,
                controllerConfiguration.getNamespaces().toArray(new String[0]));
        labelSelector =
            valueOrDefault(kubeDependent, KubernetesDependent::labelSelector, null);
        addOwnerReference =
            valueOrDefault(
                kubeDependent,
                KubernetesDependent::addOwnerReference,
                KubernetesDependent.ADD_OWNER_REFERENCE_DEFAULT);
      }

      kubernetesDependentResource.configureWith(controllerConfiguration.getConfigurationService(),
          labelSelector,
          Set.of(namespaces), addOwnerReference);
      return (T) kubernetesDependentResource;
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException
        | NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }
}
