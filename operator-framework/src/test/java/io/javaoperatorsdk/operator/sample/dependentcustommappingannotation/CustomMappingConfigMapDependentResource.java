package io.javaoperatorsdk.operator.sample.dependentcustommappingannotation;

import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public class CustomMappingConfigMapDependentResource
    extends CRUDNoGCKubernetesDependentResource<ConfigMap, DependentCustomMappingCustomResource>
    implements SecondaryToPrimaryMapper<ConfigMap> {

  private SecondaryToPrimaryMapper<ConfigMap> mapper =
      Mappers.fromAnnotation("customNameKey", "customNamespaceKey");

  public CustomMappingConfigMapDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  protected ConfigMap desired(DependentCustomMappingCustomResource primary,
      Context<DependentCustomMappingCustomResource> context) {
    return new ConfigMapBuilder()
        .withMetadata(new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build())
        .withData(Map.of("key", "val"))
        .build();
  }

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(ConfigMap resource) {
    return mapper.toPrimaryResourceIDs(resource);
  }
}
