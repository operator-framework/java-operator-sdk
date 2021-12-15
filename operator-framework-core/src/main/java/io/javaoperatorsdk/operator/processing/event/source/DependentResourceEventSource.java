package io.javaoperatorsdk.operator.processing.event.source;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public class DependentResourceEventSource<T extends HasMetadata, P extends HasMetadata>
    extends InformerEventSource<T, P>
    implements EventSourceWrapper<T>, ResourceEventSource<T, P> {
  private final DependentResourceConfiguration<T, P> configuration;

  public DependentResourceEventSource(
      FilterWatchListDeletable<T, KubernetesResourceList<T>> client,
      Cloner cloner, DependentResourceConfiguration<T, P> dependentConfiguration) {
    super(client.runnableInformer(0),
        dependentConfiguration.getPrimaryResourcesRetriever(),
        dependentConfiguration.getAssociatedResourceRetriever(),
        dependentConfiguration.skipUpdateIfUnchanged(), cloner);
    this.configuration = dependentConfiguration;
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return getCache().get(resourceID);
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return getCache().list(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    return getCache().list(namespace, predicate);
  }

  @Override
  public ResourceCache<T> getResourceCache() {
    return getCache();
  }

  @Override
  public ResourceConfiguration<T, ? extends ResourceConfiguration> getConfiguration() {
    return configuration;
  }
}
