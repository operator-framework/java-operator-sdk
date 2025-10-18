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
package io.javaoperatorsdk.operator.dependent.externalstate;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.DependentResourceWithExplicitState;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.external.PerResourcePollingDependentResource;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;

public class ExternalWithStateDependentResource
    extends PerResourcePollingDependentResource<
        ExternalResource, ExternalStateCustomResource, String>
    implements DependentResourceWithExplicitState<
            ExternalResource, ExternalStateCustomResource, ConfigMap>,
        Updater<ExternalResource, ExternalStateCustomResource> {

  ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  public ExternalWithStateDependentResource() {
    super(ExternalResource.class, Duration.ofMillis(300));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<ExternalResource> fetchResources(ExternalStateCustomResource primaryResource) {
    return getResourceID(primaryResource)
        .map(
            id -> {
              var externalResource = externalService.read(id);
              return externalResource.map(Set::of).orElseGet(Collections::emptySet);
            })
        .orElseGet(Collections::emptySet);
  }

  @Override
  protected Optional<ExternalResource> selectTargetSecondaryResource(
      Set<ExternalResource> secondaryResources,
      ExternalStateCustomResource primary,
      Context<ExternalStateCustomResource> context) {
    var id = getResourceID(primary);
    return id.flatMap(k -> secondaryResources.stream().filter(e -> e.getId().equals(k)).findAny());
  }

  private Optional<String> getResourceID(ExternalStateCustomResource primaryResource) {
    Optional<ConfigMap> configMapOptional =
        getExternalStateEventSource().getSecondaryResource(primaryResource);
    return configMapOptional.map(cm -> cm.getData().get(ExternalStateDependentReconciler.ID_KEY));
  }

  @Override
  protected ExternalResource desired(
      ExternalStateCustomResource primary, Context<ExternalStateCustomResource> context) {
    return new ExternalResource(primary.getSpec().getData());
  }

  @Override
  public Class<ConfigMap> stateResourceClass() {
    return ConfigMap.class;
  }

  @Override
  public ConfigMap stateResource(ExternalStateCustomResource primary, ExternalResource resource) {
    ConfigMap configMap =
        new ConfigMapBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                    .withName(primary.getMetadata().getName())
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
      ExternalStateCustomResource primary,
      Context<ExternalStateCustomResource> context) {
    return externalService.create(desired);
  }

  @Override
  public ExternalResource update(
      ExternalResource actual,
      ExternalResource desired,
      ExternalStateCustomResource primary,
      Context<ExternalStateCustomResource> context) {
    return externalService.update(new ExternalResource(actual.getId(), desired.getData()));
  }

  @Override
  public Matcher.Result<ExternalResource> match(
      ExternalResource resource,
      ExternalStateCustomResource primary,
      Context<ExternalStateCustomResource> context) {
    return Matcher.Result.nonComputed(resource.getData().equals(primary.getSpec().getData()));
  }

  @Override
  protected void handleDelete(
      ExternalStateCustomResource primary,
      ExternalResource secondary,
      Context<ExternalStateCustomResource> context) {
    externalService.delete(secondary.getId());
  }
}
