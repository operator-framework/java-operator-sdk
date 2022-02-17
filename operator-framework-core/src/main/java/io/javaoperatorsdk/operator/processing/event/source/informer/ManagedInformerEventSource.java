package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.ResourceConfiguration;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.UpdatableCache;

public abstract class ManagedInformerEventSource<R extends HasMetadata, P extends HasMetadata, C extends ResourceConfiguration<R>>
    extends CachingEventSource<R, P>
    implements ResourceEventHandler<R> {

  @SuppressWarnings("rawtypes")
  private static final ConcurrentMap<ResourceConfigurationAsKey, InformerManager> managedInformers =
      new ConcurrentHashMap<>(17);

  protected ManagedInformerEventSource(
      MixedOperation<R, KubernetesResourceList<R>, Resource<R>> client, C configuration) {
    super(configuration.getResourceClass());
    initCache(configuration);
    manager().initSources(client, configuration, this);
  }

  @SuppressWarnings("unchecked")
  private void initCache(C configuration) {
    final var key = new ResourceConfigurationAsKey(configuration);
    var existing = managedInformers.get(key);
    if (existing == null) {
      existing = new InformerManager<>();
      managedInformers.put(key, existing);
    }
    cache = existing;
  }

  @Override
  protected UpdatableCache<R> initCache() {
    return null; // cache needs the configuration to be properly initialized
  }

  protected InformerManager<R, C> manager() {
    return (InformerManager<R, C>) cache;
  }

  @Override
  public void start() {
    manager().start();
    super.start();
  }

  @Override
  public void stop() {
    super.stop();
    manager().stop();
  }

  @SuppressWarnings("rawtypes")
  private static class ResourceConfigurationAsKey {
    private final ResourceConfiguration configuration;

    private ResourceConfigurationAsKey(ResourceConfiguration configuration) {
      this.configuration = configuration;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      final var that = (ResourceConfigurationAsKey) o;
      if (configuration == that.configuration) {
        return true;
      }

      return Objects.equals(getLabelSelector(), that.getLabelSelector())
          && Objects.equals(getResourceClass(), that.getResourceClass())
          && Objects.equals(getNamespaces(), that.getNamespaces());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getLabelSelector(), getResourceClass(), getNamespaces());
    }

    public String getLabelSelector() {
      return configuration.getLabelSelector();
    }

    public Class getResourceClass() {
      return configuration.getResourceClass();
    }

    @SuppressWarnings("unchecked")
    public Set<String> getNamespaces() {
      return configuration.getNamespaces();
    }
  }
}
