/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/** Not using CRUDKubernetesDependentResource so the delete functionality can be tested. */
public class ConfigMapDeleterBulkDependentResource
    extends KubernetesDependentResource<ConfigMap, BulkDependentTestCustomResource>
    implements KubernetesBulkDependentResource<ConfigMap, BulkDependentTestCustomResource> {

  public static final String LABEL_KEY = "bulk";
  public static final String LABEL_VALUE = "true";
  public static final String ADDITIONAL_DATA_KEY = "additionalData";
  public static final String INDEX_DELIMITER = "-";

  @Override
  public Map<ResourceID, ConfigMap> desiredResources(
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    var number = primary.getSpec().getNumberOfResources();
    Map<ResourceID, ConfigMap> res = new HashMap<>();
    for (int i = 0; i < number; i++) {
      var desired = desired(primary, i);
      res.put(ResourceID.fromResource(desired), desired);
    }
    return res;
  }

  public ConfigMap desired(BulkDependentTestCustomResource primary, Integer key) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName() + INDEX_DELIMITER + key)
            .withNamespace(primary.getMetadata().getNamespace())
            .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
            .build());
    configMap.setData(
        Map.of("number", "" + key, ADDITIONAL_DATA_KEY, primary.getSpec().getAdditionalData()));
    return configMap;
  }

  @Override
  public Map<ResourceID, ConfigMap> getSecondaryResources(
      BulkDependentTestCustomResource primary, Context<BulkDependentTestCustomResource> context) {
    return context
        .getSecondaryResourcesAsStream(ConfigMap.class)
        .filter(cm -> getName(cm).startsWith(primary.getMetadata().getName()))
        .collect(Collectors.toMap(ResourceID::fromResource, Function.identity()));
  }

  private static String getName(ConfigMap cm) {
    return cm.getMetadata().getName();
  }
}
