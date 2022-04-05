package io.javaoperatorsdk.operator.sample.primaryindexer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

@ControllerConfiguration
public class PrimaryIndexerTestReconciler
    implements Reconciler<PrimaryIndexerTestCustomResource>,
    EventSourceInitializer<PrimaryIndexerTestCustomResource> {

  private final Map<String, AtomicInteger> numberOfExecutions = new ConcurrentHashMap<>();

  private static final String CONFIG_MAP_RELATION_INDEXER = "cm-indexer";

  @Override
  public Map<String, EventSource> prepareEventSources(
      EventSourceContext<PrimaryIndexerTestCustomResource> context) {

    context
        .getPrimaryCache()
        .addIndexers(
            Map.of(
                CONFIG_MAP_RELATION_INDEXER,
                (resource -> List.of(resource.getSpec().getConfigMapName()))));

    var informerConfiguration =
        InformerConfiguration.from(context, ConfigMap.class)
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

    return EventSourceInitializer
        .nameEventSources(new InformerEventSource<>(informerConfiguration, context));
  }

  @Override
  public UpdateControl<PrimaryIndexerTestCustomResource> reconcile(
      PrimaryIndexerTestCustomResource resource,
      Context<PrimaryIndexerTestCustomResource> context) {
    numberOfExecutions.computeIfAbsent(resource.getMetadata().getName(), r -> new AtomicInteger(0));
    numberOfExecutions.get(resource.getMetadata().getName()).incrementAndGet();
    return UpdateControl.noUpdate();
  }

  public Map<String, AtomicInteger> getNumberOfExecutions() {
    return numberOfExecutions;
  }

}
