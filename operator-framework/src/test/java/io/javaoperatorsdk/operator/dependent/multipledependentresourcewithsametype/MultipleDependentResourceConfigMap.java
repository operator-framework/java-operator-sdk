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
package io.javaoperatorsdk.operator.dependent.multipledependentresourcewithsametype;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class MultipleDependentResourceConfigMap
    extends CRUDKubernetesDependentResource<
        ConfigMap, MultipleDependentResourceCustomResourceNoDiscriminator> {

  public static final String DATA_KEY = "key";
  private final int value;

  public MultipleDependentResourceConfigMap(int value) {
    super(ConfigMap.class);
    this.value = value;
  }

  @Override
  protected ConfigMap desired(
      MultipleDependentResourceCustomResourceNoDiscriminator primary,
      Context<MultipleDependentResourceCustomResourceNoDiscriminator> context) {
    Map<String, String> data = new HashMap<>();
    data.put(DATA_KEY, String.valueOf(value));

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getConfigMapName(value))
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(data)
        .build();
  }
}
