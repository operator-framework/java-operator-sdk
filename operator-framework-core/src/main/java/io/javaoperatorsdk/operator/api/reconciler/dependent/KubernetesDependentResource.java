package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  private KubernetesClient client;
  private boolean manageDelete;
  private InformerEventSource<R, P> informerEventSource;

  public KubernetesDependentResource() {
    this(null, false);
  }

  public KubernetesDependentResource(KubernetesClient client) {
    this(client, false);
  }

  public KubernetesDependentResource(KubernetesClient client, boolean manageDelete) {
    this.client = client;
    this.manageDelete = manageDelete;
  }

  @Override
  protected R create(R target, Context context) {
    return client.resource(target).createOrReplace();
  }

  @Override
  protected R update(R actual, R target, Context context) {
    // todo map annotation and labels ?
    return client.resource(target).createOrReplace();
  }

  @Override
  public Optional<EventSource> initEventSource(EventSourceContext<P> context) {
    InformerConfiguration config = InformerConfiguration.from(context, resourceType()).build();
    informerEventSource = new InformerEventSource(config, context);
    return Optional.of(informerEventSource);
  }

  @Override
  public void delete(P primary, Context context) {
    if (manageDelete) {
      var resource = getResource(primary);
      resource.ifPresent(r -> client.resource(r).delete());
    }
  }

  @Override
  public Optional<R> getResource(P primaryResource) {
    return informerEventSource.getAssociated(primaryResource);
  }


  public KubernetesDependentResource<R, P> setClient(KubernetesClient client) {
    this.client = client;
    return this;
  }
}
