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
package io.javaoperatorsdk.operator.dependent.multipledependentsametypemultiinformer;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype.MultipleManagedDependentResourceReconciler;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;

@KubernetesDependent
public class MultipleManagedDependentResourceMultiInformerConfigMap1
    extends CRUDKubernetesDependentResource<
        ConfigMap, MultipleManagedDependentResourceMultiInformerCustomResource> {

  public static final String NAME_SUFFIX = "-1";

  @Override
  protected ConfigMap desired(
      MultipleManagedDependentResourceMultiInformerCustomResource primary,
      Context<MultipleManagedDependentResourceMultiInformerCustomResource> context) {
    Map<String, String> data = new HashMap<>();
    data.put(MultipleManagedDependentResourceReconciler.DATA_KEY, primary.getSpec().getValue());

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getMetadata().getName() + NAME_SUFFIX)
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(data)
        .build();
  }
}
