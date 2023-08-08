package io.javaoperatorsdk.operator.processing.dependent.kubernetes;

import java.util.Arrays;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Utils;
import io.javaoperatorsdk.operator.api.config.dependent.ConfigurationConverter;
import io.javaoperatorsdk.operator.api.reconciler.Constants;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.event.source.filter.GenericFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnAddFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnDeleteFilter;
import io.javaoperatorsdk.operator.processing.event.source.filter.OnUpdateFilter;

import static io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResourceConfig.DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA;

public class KubernetesDependentConverter<R extends HasMetadata, P extends HasMetadata> implements
    ConfigurationConverter<KubernetesDependent, KubernetesDependentResourceConfig<R>, KubernetesDependentResource<R, P>> {

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public KubernetesDependentResourceConfig<R> configFrom(KubernetesDependent configAnnotation,
      ControllerConfiguration<?> parentConfiguration,
      Class<KubernetesDependentResource<R, P>> originatingClass) {
    var namespaces = parentConfiguration.getNamespaces();
    var configuredNS = false;
    String labelSelector = null;
    var createResourceOnlyIfNotExistingWithSSA =
        DEFAULT_CREATE_RESOURCE_ONLY_IF_NOT_EXISTING_WITH_SSA;
    OnAddFilter<? extends HasMetadata> onAddFilter = null;
    OnUpdateFilter<? extends HasMetadata> onUpdateFilter = null;
    OnDeleteFilter<? extends HasMetadata> onDeleteFilter = null;
    GenericFilter<? extends HasMetadata> genericFilter = null;
    ResourceDiscriminator<?, ?> resourceDiscriminator = null;
    if (configAnnotation != null) {
      if (!Arrays.equals(KubernetesDependent.DEFAULT_NAMESPACES, configAnnotation.namespaces())) {
        namespaces = Set.of(configAnnotation.namespaces());
        configuredNS = true;
      }

      final var fromAnnotation = configAnnotation.labelSelector();
      labelSelector = Constants.NO_VALUE_SET.equals(fromAnnotation) ? null : fromAnnotation;

      final var context = Utils.contextFor(parentConfiguration, originatingClass,
          configAnnotation.annotationType());
      onAddFilter = Utils.instantiate(configAnnotation.onAddFilter(), OnAddFilter.class, context);
      onUpdateFilter =
          Utils.instantiate(configAnnotation.onUpdateFilter(), OnUpdateFilter.class, context);
      onDeleteFilter =
          Utils.instantiate(configAnnotation.onDeleteFilter(), OnDeleteFilter.class, context);
      genericFilter =
          Utils.instantiate(configAnnotation.genericFilter(), GenericFilter.class, context);

      resourceDiscriminator =
          Utils.instantiate(configAnnotation.resourceDiscriminator(), ResourceDiscriminator.class,
              context);
      createResourceOnlyIfNotExistingWithSSA =
          configAnnotation.createResourceOnlyIfNotExistingWithSSA();
    }

    return new KubernetesDependentResourceConfig(namespaces, labelSelector, configuredNS,
        createResourceOnlyIfNotExistingWithSSA,
        resourceDiscriminator, onAddFilter, onUpdateFilter, onDeleteFilter, genericFilter);
  }
}
