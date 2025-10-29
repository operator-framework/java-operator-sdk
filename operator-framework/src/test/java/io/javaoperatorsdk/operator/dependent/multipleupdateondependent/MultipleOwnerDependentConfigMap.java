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
package io.javaoperatorsdk.operator.dependent.multipleupdateondependent;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.BooleanWithUndefined;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@KubernetesDependent(useSSA = BooleanWithUndefined.TRUE)
public class MultipleOwnerDependentConfigMap
    extends CRUDKubernetesDependentResource<ConfigMap, MultipleOwnerDependentCustomResource> {

  public static final String RESOURCE_NAME = "test1";

  @Override
  protected ConfigMap desired(
      MultipleOwnerDependentCustomResource primary,
      Context<MultipleOwnerDependentCustomResource> context) {

    var cm = getSecondaryResource(primary, context);

    var data = cm.map(ConfigMap::getData).orElse(new HashMap<>());
    data.put(primary.getSpec().getValue(), primary.getSpec().getValue());

    return new ConfigMapBuilder()
        .withNewMetadata()
        .withName(RESOURCE_NAME)
        .withNamespace(primary.getMetadata().getNamespace())
        .withOwnerReferences(cm.map(c -> c.getMetadata().getOwnerReferences()).orElse(List.of()))
        .endMetadata()
        .withData(data)
        .build();
  }

  // need to change this since owner reference is present only for the creator primary resource.
  @Override
  public Optional<ConfigMap> getSecondaryResource(
      MultipleOwnerDependentCustomResource primary,
      Context<MultipleOwnerDependentCustomResource> context) {
    InformerEventSource<ConfigMap, MultipleOwnerDependentCustomResource> ies =
        (InformerEventSource<ConfigMap, MultipleOwnerDependentCustomResource>)
            context.eventSourceRetriever().getEventSourceFor(ConfigMap.class);
    return ies.get(new ResourceID(RESOURCE_NAME, primary.getMetadata().getNamespace()));
  }
}
