package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentConverter.class);

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

  @SuppressWarnings({"unchecked", "rawtypes"})
  private KubernetesDependentInformerConfig<R> createInformerConfig(
      KubernetesDependent configAnnotation,
      DependentResourceSpec<R, P, KubernetesDependentResourceConfig<R>> spec,
      ControllerConfiguration<? extends HasMetadata> controllerConfig) {
    Class<? extends KubernetesDependentResource<?, ?>> dependentResourceClass =
        (Class<? extends KubernetesDependentResource<?, ?>>) spec.getDependentResourceClass();

    if (configAnnotation != null && configAnnotation.informerConfig() != null) {
      var config = new KubernetesDependentInformerConfigBuilder<>();

      // override default name if more specific one is provided
      if (!Constants.NO_VALUE_SET.equals(configAnnotation.informerConfig().name())) {
        config.withName(configAnnotation.informerConfig().name());
      }

      var namespaces = Set.of(configAnnotation.informerConfig().namespaces());
      config.withNamespaces(namespaces);

      final var fromAnnotation = configAnnotation.informerConfig().labelSelector();
      var labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;
      config.withLabelSelector(labelSelector);

      final var context = Utils.contextFor(controllerConfig, dependentResourceClass,
          configAnnotation.annotationType());

      var onAddFilter = Utils.instantiate(configAnnotation.informerConfig().onAddFilter(),
          OnAddFilter.class, context);
      config.withOnAddFilter(onAddFilter);

      var onUpdateFilter =
          Utils.instantiate(configAnnotation.informerConfig().onUpdateFilter(),
              OnUpdateFilter.class, context);
      config.withOnUpdateFilter(onUpdateFilter);

      var onDeleteFilter =
          Utils.instantiate(configAnnotation.informerConfig().onDeleteFilter(),
              OnDeleteFilter.class, context);
      config.withOnDeleteFilter(onDeleteFilter);

      var genericFilter =
          Utils.instantiate(configAnnotation.informerConfig().genericFilter(),
              GenericFilter.class,
              context);

      config.withGenericFilter(genericFilter);

      config.withFollowControllerNamespacesOnChange(
          configAnnotation.informerConfig().followControllerNamespacesOnChange());

      return config.build();
    }
    return new KubernetesDependentInformerConfigBuilder<>().build();
  }

}
