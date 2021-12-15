package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.dependent.DefaultDependentResourceConfiguration;
import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;

public class DependentResource<R extends HasMetadata, P extends HasMetadata>
    implements Builder<R, P>, Updater<R, P> {

  private final DefaultDependentResourceConfiguration<R, P> configuration;
  private final Builder<R, P> builder;
  private final Updater<R, P> updater;
  private final Fetcher<R> fetcher;
  private ResourceEventSource<R, P> source;

  public DependentResource(DefaultDependentResourceConfiguration<R, P> configuration,
      Builder<R, P> builder, Updater<R, P> updater, Fetcher<R> fetcher) {
    this.configuration = configuration;
    this.builder = builder;
    this.updater = updater;
    this.fetcher = fetcher;
  }

  @Override
  public R buildFor(P primary) {
    return builder.buildFor(primary);
  }

  public ResourceCache<R> getCache() {
    return source.getResourceCache();
  }

  public R fetchFor(HasMetadata owner) {
    return fetcher != null ? fetcher.fetchFor(owner, getCache())
        : getCache().get(ResourceID.fromResource(owner)).orElse(null);
  }

  public DependentResourceConfiguration<R, P> getConfiguration() {
    return configuration;
  }

  @Override
  public R update(R fetched, P primary) {
    return updater.update(fetched, primary);
  }

  public void setSource(ResourceEventSource<R, P> source) {
    this.source = source;
  }
}
