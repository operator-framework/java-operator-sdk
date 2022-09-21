package io.javaoperatorsdk.operator.sample.bulkdependent;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.BulkResourceDiscriminatorFactory;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

/**
 * Not using CRUDKubernetesDependentResource so the delete functionality can be tested.
 */
public class ConfigMapDeleterBulkDependentResource
    extends
    KubernetesDependentResource<ConfigMap, BulkDependentTestCustomResource>
    implements Creator<ConfigMap, BulkDependentTestCustomResource>,
    Updater<ConfigMap, BulkDependentTestCustomResource>,
    Deleter<BulkDependentTestCustomResource>,
    BulkDependentResource<ConfigMap, BulkDependentTestCustomResource> {

  public static final String LABEL_KEY = "bulk";
  public static final String LABEL_VALUE = "true";
  public static final String ADDITIONAL_DATA_KEY = "additionalData";
  private BulkResourceDiscriminatorFactory<ConfigMap, BulkDependentTestCustomResource> factory =
      index -> (resource, primary, context) -> {
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

  public ConfigMapDeleterBulkDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  public ConfigMap desired(BulkDependentTestCustomResource primary,
      int index, Context<BulkDependentTestCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName() + "-" + index)
        .withNamespace(primary.getMetadata().getNamespace())
        .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
        .build());
    configMap.setData(
        Map.of("number", "" + index, ADDITIONAL_DATA_KEY, primary.getSpec().getAdditionalData()));
    return configMap;
  }

  @Override
  public int count(BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    return primary.getSpec().getNumberOfResources();
  }

  @Override
  public BulkResourceDiscriminatorFactory<ConfigMap, BulkDependentTestCustomResource> bulkResourceDiscriminatorFactory() {
    return factory;
  }
}
