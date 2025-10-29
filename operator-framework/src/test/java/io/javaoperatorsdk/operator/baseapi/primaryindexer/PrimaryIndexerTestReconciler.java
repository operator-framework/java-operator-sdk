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
package io.javaoperatorsdk.operator.baseapi.primaryindexer;

import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerEventSourceConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class PrimaryIndexerTestReconciler extends AbstractPrimaryIndexerTestReconciler {

  @Override
  public List<EventSource<?, PrimaryIndexerTestCustomResource>> prepareEventSources(
      EventSourceContext<PrimaryIndexerTestCustomResource> context) {

    context.getPrimaryCache().addIndexer(CONFIG_MAP_RELATION_INDEXER, indexer);

    var informerConfiguration =
        InformerEventSourceConfiguration.from(
                ConfigMap.class, PrimaryIndexerTestCustomResource.class)
            .withSecondaryToPrimaryMapper(
                (ConfigMap secondaryResource) ->
                    context
                        .getPrimaryCache()
                        .byIndex(
                            CONFIG_MAP_RELATION_INDEXER, secondaryResource.getMetadata().getName())
                        .stream()
                        .map(ResourceID::fromResource)
                        .collect(Collectors.toSet()))
            .build();

    return List.of(new InformerEventSource<>(informerConfiguration, context));
  }
}
