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
package io.javaoperatorsdk.operator.dependent.primaryindexer;

import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.baseapi.primaryindexer.AbstractPrimaryIndexerTestReconciler;
import io.javaoperatorsdk.operator.baseapi.primaryindexer.PrimaryIndexerTestCustomResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.dependent.primaryindexer.DependentPrimaryIndexerTestReconciler.CONFIG_MAP_EVENT_SOURCE;

@Workflow(
    dependents =
        @Dependent(
            useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE,
            type = DependentPrimaryIndexerTestReconciler.ReadOnlyConfigMapDependent.class))
@ControllerConfiguration
public class DependentPrimaryIndexerTestReconciler extends AbstractPrimaryIndexerTestReconciler
    implements Reconciler<PrimaryIndexerTestCustomResource> {

  public static final String CONFIG_MAP_EVENT_SOURCE = "configMapEventSource";

  @Override
  public List<EventSource<?, PrimaryIndexerTestCustomResource>> prepareEventSources(
      EventSourceContext<PrimaryIndexerTestCustomResource> context) {

    var cache = context.getPrimaryCache();
    cache.addIndexer(CONFIG_MAP_RELATION_INDEXER, indexer);

    InformerEventSource<ConfigMap, PrimaryIndexerTestCustomResource> es =
        new InformerEventSource<>(
            InformerEventSourceConfiguration.from(
                    ConfigMap.class, PrimaryIndexerTestCustomResource.class)
                .withName(CONFIG_MAP_EVENT_SOURCE)
                .withSecondaryToPrimaryMapper(
                    resource ->
                        cache
                            .byIndex(CONFIG_MAP_RELATION_INDEXER, resource.getMetadata().getName())
                            .stream()
                            .map(ResourceID::fromResource)
                            .collect(Collectors.toSet()))
                .build(),
            context);

    return List.of(es);
  }

  public static class ReadOnlyConfigMapDependent
      extends KubernetesDependentResource<ConfigMap, PrimaryIndexerTestCustomResource> {

    @Override
    protected ConfigMap desired(
        PrimaryIndexerTestCustomResource primary,
        Context<PrimaryIndexerTestCustomResource> context) {
      return new ConfigMapBuilder()
          .withMetadata(
              new ObjectMetaBuilder()
                  .withName(CONFIG_MAP_NAME)
                  .withNamespace(primary.getMetadata().getNamespace())
                  .build())
          .build();
    }
  }
}
