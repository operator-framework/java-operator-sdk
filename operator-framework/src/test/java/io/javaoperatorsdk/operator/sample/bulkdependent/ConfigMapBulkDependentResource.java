package io.javaoperatorsdk.operator.sample.bulkdependent;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.BulkResourceDiscriminatorFactory;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class ConfigMapBulkDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, BulkDependentTestCustomResource>
    implements BulkDependentResource<ConfigMap, BulkDependentTestCustomResource> {

  private final static Logger log = LoggerFactory.getLogger(ConfigMapBulkDependentResource.class);

  public static final String LABEL_KEY = "bulk";
  public static final String LABEL_VALUE = "true";
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

  public ConfigMapBulkDependentResource() {
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
    configMap.setData(Map.of("number", "" + index));
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
