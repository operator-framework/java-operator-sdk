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
package io.javaoperatorsdk.operator.dependent.bulkdependent.readonly;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.processing.dependent.KubernetesBulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

@KubernetesDependent
public class ReadOnlyBulkDependentResource
    extends KubernetesDependentResource<ConfigMap, BulkDependentTestCustomResource>
    implements KubernetesBulkDependentResource<ConfigMap, BulkDependentTestCustomResource>,
        SecondaryToPrimaryMapper<ConfigMap> {

  public static final String INDEX_DELIMITER = "-";

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

  @Override
  public Set<ResourceID> toPrimaryResourceIDs(ConfigMap resource) {
    return Mappers.fromOwnerReferences(BulkDependentTestCustomResource.class, false)
        .toPrimaryResourceIDs(resource);
  }
}
