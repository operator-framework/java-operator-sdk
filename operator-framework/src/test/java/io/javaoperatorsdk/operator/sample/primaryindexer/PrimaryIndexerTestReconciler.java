package io.javaoperatorsdk.operator.sample.primaryindexer;

import java.util.Map;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class PrimaryIndexerTestReconciler
    extends AbstractPrimaryIndexerTestReconciler {

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<PrimaryIndexerTestCustomResource> context) {

    context.getPrimaryCache().addIndexer(CONFIG_MAP_RELATION_INDEXER, indexer);

    var informerConfiguration =
        InformerConfiguration.from(ConfigMap.class)
            .withSecondaryToPrimaryMapper(
                (ConfigMap secondaryResource) -> context
                    .getPrimaryCache()
                    .byIndex(
                        CONFIG_MAP_RELATION_INDEXER,
                        secondaryResource.getMetadata().getName())
                    .stream()
                    .map(ResourceID::fromResource)
                    .collect(Collectors.toSet()))
            .build();

    return Reconciler
        .nameEventSources(new InformerEventSource<>(informerConfiguration, context));
  }
}
