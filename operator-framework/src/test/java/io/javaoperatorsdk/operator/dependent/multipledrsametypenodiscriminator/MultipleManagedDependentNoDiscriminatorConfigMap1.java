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
package io.javaoperatorsdk.operator.dependent.multipledrsametypenodiscriminator;

import java.util.HashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

@KubernetesDependent
public class MultipleManagedDependentNoDiscriminatorConfigMap1
    extends CRUDKubernetesDependentResource<
        ConfigMap, MultipleManagedDependentNoDiscriminatorCustomResource> {

  public static final String NAME_SUFFIX = "-1";

  /*
   * Showcases optimization to avoid computing the whole desired state by providing the ResourceID
   * of the target resource. In this particular case this would not be necessary, since desired
   * state creation is pretty lightweight. However, this might make sense in situation where the
   * desired state is more costly
   */
  protected ResourceID targetSecondaryResourceID(
      MultipleManagedDependentNoDiscriminatorCustomResource primary,
      Context<MultipleManagedDependentNoDiscriminatorCustomResource> context) {
    return new ResourceID(
        primary.getMetadata().getName() + NAME_SUFFIX, primary.getMetadata().getNamespace());
  }

  @Override
  protected ConfigMap desired(
      MultipleManagedDependentNoDiscriminatorCustomResource primary,
      Context<MultipleManagedDependentNoDiscriminatorCustomResource> context) {
    Map<String, String> data = new HashMap<>();
    data.put(
        MultipleManagedDependentSameTypeNoDiscriminatorReconciler.DATA_KEY,
        primary.getSpec().getValue());

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(primary.getMetadata().getName() + NAME_SUFFIX)
        .withNamespace(primary.getMetadata().getNamespace())
        .endMetadata()
        .withData(data)
        .build();
  }
}
