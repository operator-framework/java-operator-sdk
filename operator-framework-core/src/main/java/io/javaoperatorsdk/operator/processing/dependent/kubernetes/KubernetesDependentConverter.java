package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.ConfigurationConverter;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig.DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA;

public class KubernetesDependentConverter<R extends HasMetadata, P extends HasMetadata> implements
    ConfigurationConverter<KubernetesDependent, KubernetesDependentResourceConfig<R>> {

  @Override
  @SuppressWarnings("unchecked")
  public KubernetesDependentResourceConfig<R> configFrom(KubernetesDependent configAnnotation,
      DependentResourceSpec<?, ?, KubernetesDependentResourceConfig<R>> spec,
      ControllerConfiguration<?> controllerConfig) {
    var createResourceOnlyIfNotExistingWithSSA =
        DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA;

    Boolean useSSA = null;
    if (configAnnotation != null) {
      createResourceOnlyIfNotExistingWithSSA =
          configAnnotation.createResourceOnlyIfNotExistingWithSSA();
      useSSA = configAnnotation.useSSA().asBoolean();
    }

    var informerConfiguration = createInformerConfig(configAnnotation,
        (DependentResourceSpec<R, P, KubernetesDependentResourceConfig<R>>) spec,
        controllerConfig);

    return new KubernetesDependentResourceConfig<>(useSSA, createResourceOnlyIfNotExistingWithSSA,
        informerConfiguration);
  }

  @SuppressWarnings({"unchecked"})
  private InformerConfiguration<R> createInformerConfig(
      KubernetesDependent configAnnotation,
      DependentResourceSpec<R, P, KubernetesDependentResourceConfig<R>> spec,
      ControllerConfiguration<? extends HasMetadata> controllerConfig) {
    Class<? extends KubernetesDependentResource<?, ?>> dependentResourceClass =
        (Class<? extends KubernetesDependentResource<?, ?>>) spec.getDependentResourceClass();

    InformerConfiguration<R>.Builder config = InformerConfiguration.builder();
    if (configAnnotation != null) {
      final var informerConfig = configAnnotation.informer();
      final var context = Utils.contextFor(controllerConfig, dependentResourceClass,
          configAnnotation.annotationType());
      config = config.initFromAnnotation(informerConfig, context);
    }
    return config.buildForInformerEventSource();
  }
}
