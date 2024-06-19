package io.javaoperatorsdk.operator.sample.primaryindexer;

import java.util.List;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

import static io.javaoperatorsdk.operator.sample.primaryindexer.DependentPrimaryIndexerTestReconciler.CONFIG_MAP_EVENT_SOURCE;

@Workflow(dependents = @Dependent(useEventSourceWithName = CONFIG_MAP_EVENT_SOURCE,
    type = DependentPrimaryIndexerTestReconciler.ReadOnlyConfigMapDependent.class))
@ControllerConfiguration
public class DependentPrimaryIndexerTestReconciler extends AbstractPrimaryIndexerTestReconciler
    implements
    Reconciler<PrimaryIndexerTestCustomResource> {

  public static final String CONFIG_MAP_EVENT_SOURCE = "configMapEventSource";

  @Override
  public List<EventSource<?, PrimaryIndexerTestCustomResource>> prepareEventSources(
      EventSourceContext<PrimaryIndexerTestCustomResource> context) {

    var cache = context.getPrimaryCache();
    cache.addIndexer(CONFIG_MAP_RELATION_INDEXER, indexer);

    InformerEventSource<ConfigMap, PrimaryIndexerTestCustomResource> es =
        new InformerEventSource<>(
            InformerConfiguration.from(ConfigMap.class, PrimaryIndexerTestCustomResource.class)
                .withName(CONFIG_MAP_EVENT_SOURCE)
                .withSecondaryToPrimaryMapper(resource -> cache
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

    public ReadOnlyConfigMapDependent() {
      super(ConfigMap.class);
    }

    @Override
    protected ConfigMap desired(PrimaryIndexerTestCustomResource primary,
        Context<PrimaryIndexerTestCustomResource> context) {
      return new ConfigMapBuilder()
          .withMetadata(new ObjectMetaBuilder()
              .withName(CONFIG_MAP_NAME)
              .withNamespace(primary.getMetadata().getNamespace())
              .build())
          .build();
    }
  }
}
