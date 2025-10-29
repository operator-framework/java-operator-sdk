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
package io.javaoperatorsdk.operator.workflow.multipledependentwithactivation;

import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent(informer = @Informer(name = "configMapInformer"))
public class ConfigMapDependentResource1
    extends CRUDNoGCKubernetesDependentResource<
        ConfigMap, MultipleDependentActivationCustomResource> {

  public static final String DATA_KEY = "data";
  public static final String SUFFIX = "1";

  @Override
  protected ConfigMap desired(
      MultipleDependentActivationCustomResource primary,
      Context<MultipleDependentActivationCustomResource> context) {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName() + SUFFIX)
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    configMap.setData(Map.of(DATA_KEY, primary.getSpec().getValue() + SUFFIX));
    return configMap;
  }
}
