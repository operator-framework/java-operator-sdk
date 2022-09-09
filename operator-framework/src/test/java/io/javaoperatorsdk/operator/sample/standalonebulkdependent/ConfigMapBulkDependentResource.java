package io.javaoperatorsdk.operator.sample.standalonebulkdependent;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ResourceDiscriminator;
import io.javaoperatorsdk.operator.processing.dependent.BulkResourceDiscriminatorFactory;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class ConfigMapBulkDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, StandaloneBulkDependentTestCustomResource> {

  public static final String LABEL_KEY = "bulk";
  public static final String LABEL_VALUE = "true";

  public ConfigMapBulkDependentResource() {
    super(ConfigMap.class);
    setBulkResourceDiscriminatorFactory(
        new BulkResourceDiscriminatorFactory<ConfigMap, StandaloneBulkDependentTestCustomResource>() {
          @Override
          public ResourceDiscriminator<ConfigMap, StandaloneBulkDependentTestCustomResource> createResourceDiscriminator(
              int index) {
            return (resource, primary, context) -> {
              var resources = context.getSecondaryResources(resource).stream()
                  .filter(r -> r.getMetadata().getName().endsWith("-" + index))
                  .collect(Collectors.toList());
              if (resources.isEmpty()) {
                return Optional.empty();
              } else if (resources.size() > 1) {
                throw new IllegalStateException("More than one resource found for index:" + index);
              } else {
                return Optional.of(resources.get(0));
              }
            };
          }
        });
  }

  @Override
  protected ConfigMap desired(StandaloneBulkDependentTestCustomResource primary,
      int index, Context<StandaloneBulkDependentTestCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName() + "-" + index)
        .withNamespace(primary.getMetadata().getNamespace())
        .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
        .build());
    configMap.setData(Map.of("number", "" + index));
    return configMap;
  }

  @Override
  protected boolean isBulkResourceCreation(StandaloneBulkDependentTestCustomResource primary,
      Context<StandaloneBulkDependentTestCustomResource> context) {
    return true;
  }

  @Override
  protected int count(StandaloneBulkDependentTestCustomResource primary,
      Context<StandaloneBulkDependentTestCustomResource> context) {
    return primary.getSpec().getNumberOfResources();
  }


}
