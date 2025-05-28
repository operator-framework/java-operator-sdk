package io.javaoperatorsdk.operator.dependent.bulkdependent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.*;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;

/** Not using CRUDKubernetesDependentResource so the delete functionality can be tested. */
public class ConfigMapDeleterBulkDependentResource
    extends KubernetesDependentResource<ConfigMap, BulkDependentTestCustomResource>
    implements CRUDBulkDependentResource<ConfigMap, BulkDependentTestCustomResource> {

  public static final String LABEL_KEY = "bulk";
  public static final String LABEL_VALUE = "true";
  public static final String ADDITIONAL_DATA_KEY = "additionalData";
  public static final String INDEX_DELIMITER = "-";

  @Override
  public Map<String, ConfigMap> desiredResources(
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    var number = primary.getSpec().getNumberOfResources();
    Map<String, ConfigMap> res = new HashMap<>();
    for (int i = 0; i < number; i++) {
      var key = Integer.toString(i);
      res.put(key, desired(primary, key));
    }
    return res;
  }

  public ConfigMap desired(BulkDependentTestCustomResource primary, String key) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName() + INDEX_DELIMITER + key)
            .withNamespace(primary.getMetadata().getNamespace())
            .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
            .build());
    configMap.setData(
        Map.of("number", key, ADDITIONAL_DATA_KEY, primary.getSpec().getAdditionalData()));
    return configMap;
  }

  @Override
  public Map<String, ConfigMap> getSecondaryResources(
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    return context
        .getSecondaryResourcesAsStream(ConfigMap.class)
        .filter(cm -> getName(cm).startsWith(primary.getMetadata().getName()))
        .collect(
            Collectors.toMap(
                cm -> getName(cm).substring(getName(cm).lastIndexOf(INDEX_DELIMITER) + 1),
                Function.identity()));
  }

  private static String getName(ConfigMap cm) {
    return cm.getMetadata().getName();
  }
}
