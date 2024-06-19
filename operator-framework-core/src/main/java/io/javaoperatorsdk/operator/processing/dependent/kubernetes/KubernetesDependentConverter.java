package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.ConfigurationConverter;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceSpec;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

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
  private KubernetesDependentInformerConfig<R> createInformerConfig(
      KubernetesDependent configAnnotation,
      DependentResourceSpec<R, P, KubernetesDependentResourceConfig<R>> spec,
      ControllerConfiguration<? extends HasMetadata> controllerConfig) {
    Class<? extends KubernetesDependentResource<?, ?>> dependentResourceClass =
        (Class<? extends KubernetesDependentResource<?, ?>>) spec.getDependentResourceClass();

    final var config = new KubernetesDependentInformerConfigBuilder<R>();
    if (configAnnotation != null) {
      final var informerConfig = configAnnotation.informerConfig();
      if (informerConfig != null) {

        // override default name if more specific one is provided
        if (!Constants.NO_VALUE_SET.equals(informerConfig.name())) {
          config.withName(informerConfig.name());
        }

        var namespaces = Set.of(informerConfig.namespaces());
        config.withNamespaces(namespaces);

        final var fromAnnotation = informerConfig.labelSelector();
        var labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;
        config.withLabelSelector(labelSelector);

        final var context = Utils.contextFor(controllerConfig, dependentResourceClass,
            configAnnotation.annotationType());

        var onAddFilter = Utils.instantiate(informerConfig.onAddFilter(),
            OnAddFilter.class, context);
        config.withOnAddFilter(onAddFilter);

        var onUpdateFilter =
            Utils.instantiate(informerConfig.onUpdateFilter(),
                OnUpdateFilter.class, context);
        config.withOnUpdateFilter(onUpdateFilter);

        var onDeleteFilter =
            Utils.instantiate(informerConfig.onDeleteFilter(),
                OnDeleteFilter.class, context);
        config.withOnDeleteFilter(onDeleteFilter);

        var genericFilter =
            Utils.instantiate(informerConfig.genericFilter(),
                GenericFilter.class,
                context);

        config.withGenericFilter(genericFilter);

        config.withFollowControllerNamespacesOnChange(
            informerConfig.followControllerNamespacesOnChange());
      }
    }
    return config.build();
  }

}
