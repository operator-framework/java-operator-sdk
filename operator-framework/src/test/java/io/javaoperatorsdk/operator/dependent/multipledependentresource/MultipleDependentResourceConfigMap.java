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
package io.javaoperatorsdk.operator.dependent.multipledependentresource;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class MultipleDependentResourceConfigMap
    extends CRUDKubernetesDependentResource<ConfigMap, MultipleDependentResourceCustomResource> {

  public static final String DATA_KEY = "key";
  private final String value;

  public MultipleDependentResourceConfigMap(String value) {
    super(ConfigMap.class);
    this.value = value;
  }

  @Override
  protected ConfigMap desired(
      MultipleDependentResourceCustomResource primary,
      Context<MultipleDependentResourceCustomResource> context) {

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(getConfigMapName(value))
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(Map.of(DATA_KEY, primary.getSpec().getValue()))
        .build();
  }

  public static String getConfigMapName(String id) {
    return "configmap" + id;
  }
}
