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
package io.javaoperatorsdk.operator.dependent.externalstate.externalstatebulkdependent;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.dependent.externalstate.ExternalStateDependentReconciler;
import io.javaoperatorsdk.operator.processing.dependent.*;
import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;

public class BulkDependentResourceExternalWithState
    extends PerResourcePollingDependentResource<
        ExternalResource, ExternalStateBulkDependentCustomResource, String>
    implements BulkDependentResource<
            ExternalResource, ExternalStateBulkDependentCustomResource, String>,
        CRUDBulkDependentResource<
            ExternalResource, ExternalStateBulkDependentCustomResource, String>,
        DependentResourceWithExplicitState<
            ExternalResource, ExternalStateBulkDependentCustomResource, ConfigMap> {

  public static final String DELIMITER = "-";
  ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  public BulkDependentResourceExternalWithState() {
    super(ExternalResource.class, Duration.ofMillis(300));
    setResourceIDMapper(this::externalResourceIndex);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<ExternalResource> fetchResources(
      ExternalStateBulkDependentCustomResource primaryResource) {
    Set<ConfigMap> configMaps =
        getExternalStateEventSource().getSecondaryResources(primaryResource);
    Set<ExternalResource> res = new HashSet<>();

    configMaps.forEach(
        cm -> {
          var id = cm.getData().get(ExternalStateDependentReconciler.ID_KEY);
          var externalResource = externalService.read(id);
          externalResource.ifPresent(res::add);
        });
    return res;
  }

  @Override
  public Class<ConfigMap> stateResourceClass() {
    return ConfigMap.class;
  }

  @Override
  public ConfigMap stateResource(
      ExternalStateBulkDependentCustomResource primary, ExternalResource resource) {
    ConfigMap configMap =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(configMapName(primary, resource))
                    .withNamespace(primary.getMetadata().getNamespace())
                    .build())
            .withData(Map.of(ExternalStateDependentReconciler.ID_KEY, resource.getId()))
            .build();
    configMap.addOwnerReference(primary);
    return configMap;
  }

  @Override
  public ExternalResource create(
      ExternalResource desired,
      ExternalStateBulkDependentCustomResource primary,
      Context<ExternalStateBulkDependentCustomResource> context) {
    return externalService.create(desired);
  }

  @Override
  public ExternalResource update(
      ExternalResource actual,
      ExternalResource desired,
      ExternalStateBulkDependentCustomResource primary,
      Context<ExternalStateBulkDependentCustomResource> context) {
    return externalService.update(new ExternalResource(actual.getId(), desired.getData()));
  }

  @Override
  protected void handleDelete(
      ExternalStateBulkDependentCustomResource primary,
      ExternalResource secondary,
      Context<ExternalStateBulkDependentCustomResource> context) {
    externalService.delete(secondary.getId());
  }

  @Override
  public Matcher.Result<ExternalResource> match(
      ExternalResource actualResource,
      ExternalResource desired,
      ExternalStateBulkDependentCustomResource primary,
      Context<ExternalStateBulkDependentCustomResource> context) {
    return Matcher.Result.computed(desired.getData().equals(actualResource.getData()), desired);
  }

  @Override
  public Map<String, ExternalResource> desiredResources(
      ExternalStateBulkDependentCustomResource primary,
      Context<ExternalStateBulkDependentCustomResource> context) {
    int number = primary.getSpec().getNumber();
    Map<String, ExternalResource> res = new HashMap<>();
    for (int i = 0; i < number; i++) {
      res.put(
          Integer.toString(i), new ExternalResource(primary.getSpec().getData() + DELIMITER + i));
    }
    return res;
  }

  @Override
  public Map<String, ExternalResource> getSecondaryResources(
      ExternalStateBulkDependentCustomResource primary,
      Context<ExternalStateBulkDependentCustomResource> context) {
    var resources = context.getSecondaryResources(ExternalResource.class);
    return resources.stream().collect(Collectors.toMap(this::externalResourceIndex, r -> r));
  }

  @Override
  public void handleDeleteTargetResource(
      ExternalStateBulkDependentCustomResource primary,
      ExternalResource resource,
      String key,
      Context<ExternalStateBulkDependentCustomResource> context) {
    externalService.delete(resource.getId());
  }

  private String externalResourceIndex(ExternalResource externalResource) {
    return externalResource
        .getData()
        .substring(externalResource.getData().lastIndexOf(DELIMITER) + 1);
  }

  private String configMapName(
      ExternalStateBulkDependentCustomResource primary, ExternalResource resource) {
    return primary.getMetadata().getName() + DELIMITER + externalResourceIndex(resource);
  }
}
