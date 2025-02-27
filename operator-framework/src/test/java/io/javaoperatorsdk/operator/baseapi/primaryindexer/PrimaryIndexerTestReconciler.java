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
