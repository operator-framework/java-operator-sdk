package io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation;

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@KubernetesDependent
public class CustomMappingConfigMapDependentResource
    extends CRUDNoGCKubernetesDependentResource<ConfigMap, DependentCustomMappingCustomResource>
    implements SecondaryToPrimaryMapper<ConfigMap> {

  public static final String CUSTOM_NAME_KEY = "customNameKey";
  public static final String CUSTOM_NAMESPACE_KEY = "customNamespaceKey";
  public static final String CUSTOM_TYPE_KEY = "customTypeKey";
  public static final String KEY = "key";

  private static final SecondaryToPrimaryMapper<ConfigMap> mapper =
      Mappers.fromAnnotation(
          CUSTOM_NAME_KEY,
          CUSTOM_NAMESPACE_KEY,
          CUSTOM_TYPE_KEY,
          DependentCustomMappingCustomResource.class);

  @Override
  protected ConfigMap desired(
      DependentCustomMappingCustomResource primary,
      Context<DependentCustomMappingCustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(
            new ObjectMetaBuilder()
                .withName(primary.getMetadata().getName())
                .withNamespace(primary.getMetadata().getNamespace())
                .build())
        .withData(Map.of(KEY, primary.getSpec().getValue()))
        .build();
  }

  @Override
  protected void addSecondaryToPrimaryMapperAnnotations(
      ConfigMap desired, DependentCustomMappingCustomResource primary) {
    addSecondaryToPrimaryMapperAnnotations(
        desired, primary, CUSTOM_NAME_KEY, CUSTOM_NAMESPACE_KEY, CUSTOM_TYPE_KEY);
  }

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(ConfigMap resource) {
    return mapper.toPrimaryResourceIDs(resource);
  }
}
