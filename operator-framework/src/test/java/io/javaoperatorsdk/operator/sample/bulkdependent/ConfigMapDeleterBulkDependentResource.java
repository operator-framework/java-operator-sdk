package io.javaoperatorsdk.operator.sample.bulkdependent;

import java.util.*;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Deleter;
import io.javaoperatorsdk.operator.processing.dependent.BulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
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
    BulkDependentResource<ConfigMap, BulkDependentTestCustomResource, Integer> {

  public static final String LABEL_KEY = "bulk";
  public static final String LABEL_VALUE = "true";
  public static final String ADDITIONAL_DATA_KEY = "additionalData";
  public static final String INDEX_DELIMITER = "-";

  public ConfigMapDeleterBulkDependentResource() {
    super(ConfigMap.class);
  }

  @Override
  public Set<Integer> targetKeys(BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    var number = primary.getSpec().getNumberOfResources();
    Set<Integer> res = new HashSet<>();
    for (int i = 0; i < number; i++) {
      res.add(i);
    }
    return res;
  }

  @Override
  public ConfigMap desired(BulkDependentTestCustomResource primary, Integer key,
      Context<BulkDependentTestCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMetaBuilder()
        .withName(primary.getMetadata().getName() + INDEX_DELIMITER + key)
        .withNamespace(primary.getMetadata().getNamespace())
        .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
        .build());
    configMap.setData(
        Map.of("number", "" + key, ADDITIONAL_DATA_KEY, primary.getSpec().getAdditionalData()));
    return configMap;
  }

  // todo fix generics?
  @Override
  public Matcher.Result<ConfigMap> match(ConfigMap actualResource,
      BulkDependentTestCustomResource primary,
      Integer index, Context<BulkDependentTestCustomResource> context) {
    return super.match(actualResource, primary, index, context);
  }

  @Override
  public Map<Integer, ConfigMap> getSecondaryResources(BulkDependentTestCustomResource primary,
      Context<BulkDependentTestCustomResource> context) {
    var configMaps = context.getSecondaryResources(ConfigMap.class);
    Map<Integer, ConfigMap> result = new HashMap<>(configMaps.size());
    configMaps.forEach(cm -> {
      String name = cm.getMetadata().getName();
      if (name.startsWith(primary.getMetadata().getName())) {
        String key = name.substring(name.lastIndexOf(INDEX_DELIMITER) + 1);
        result.put(Integer.parseInt(key), cm);
      }
    });
    return result;
  }
}
