package io.javaoperatorsdk.operator.sample.primaryindexer;

import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.IndexerResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.SecondaryToPrimaryMapper;

@ControllerConfiguration(dependents = @Dependent(
    type = DependentPrimaryIndexerTestReconciler.ReadOnlyConfigMapDependent.class))
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
    public Set<ResourceID> toPrimaryResourceIDs(ConfigMap secondaryResource) {
      return cache.byIndex(CONFIG_MAP_RELATION_INDEXER, secondaryResource.getMetadata().getName())
          .stream()
          .map(ResourceID::fromResource)
          .collect(Collectors.toSet());
    }

    @Override
    public EventSource initEventSource(
        EventSourceContext<PrimaryIndexerTestCustomResource> context) {
      cache = context.getPrimaryCache();
      cache.addIndexer(CONFIG_MAP_RELATION_INDEXER, indexer);
      return super.initEventSource(context);
    }
  }
}
