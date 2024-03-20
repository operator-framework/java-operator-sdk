package io.javaoperatorsdk.operator.sample.primaryindexer;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Workflow(dependents = @Dependent(
    type = DependentPrimaryIndexerTestReconciler.ReadOnlyConfigMapDependent.class))
@ControllerConfiguration
public class DependentPrimaryIndexerTestReconciler extends AbstractPrimaryIndexerTestReconciler
    implements
    Reconciler<PrimaryIndexerTestCustomResource> {

  public static class ReadOnlyConfigMapDependent
      extends KubernetesDependentResource<ConfigMap, PrimaryIndexerTestCustomResource> implements
      SecondaryToPrimaryMapper<ConfigMap> {
    private IndexerResourceCache<PrimaryIndexerTestCustomResource> cache;

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

    @Override
    public Set<ResourceID> toPrimaryResourceIDs(ConfigMap resource) {
      return cache.byIndex(CONFIG_MAP_RELATION_INDEXER, resource.getMetadata().getName())
          .stream()
          .map(ResourceID::fromResource)
          .collect(Collectors.toSet());
    }

    @Override
    public Optional<InformerEventSource<ConfigMap, PrimaryIndexerTestCustomResource>> eventSource(
        EventSourceContext<PrimaryIndexerTestCustomResource> context) {
      cache = context.getPrimaryCache();
      cache.addIndexer(CONFIG_MAP_RELATION_INDEXER, indexer);
      return super.eventSource(context);
    }
  }
}
