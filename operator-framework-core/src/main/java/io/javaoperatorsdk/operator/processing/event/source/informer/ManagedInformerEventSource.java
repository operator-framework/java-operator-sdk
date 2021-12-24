package io.javaoperatorsdk.operator.processing.event.source.informer;

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

  protected ManagedInformerEventSource(
      MixedOperation<R, KubernetesResourceList<R>, Resource<R>> client, C configuration) {
    super(configuration.getResourceClass());
    manager().initSources(client, configuration, this);
  }

  @Override
  protected UpdatableCache<R> initCache() {
    return new InformerManager<>();
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
}
