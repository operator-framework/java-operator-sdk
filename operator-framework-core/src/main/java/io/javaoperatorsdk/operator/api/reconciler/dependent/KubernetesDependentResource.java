package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.Mappers;

public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  protected KubernetesClient client;
  private boolean explicitDelete = false;
  private boolean owned = true;
  private InformerEventSource<R, P> informerEventSource;

  public KubernetesDependentResource() {
    this(null);
  }

  public KubernetesDependentResource(KubernetesClient client) {
    this.client = client;
  }

  protected void postProcessDesired(R desired, P primary) {
    if (owned) {
      desired.addOwnerReference(primary);
    }
  }

  @Override
  protected boolean match(R actual, R target, Context context) {
    return ReconcilerUtils.specsEqual(actual, target);
  }

  @Override
  protected R create(R target, P primary, Context context) {
    log.debug("Creating target resource with type: " +
        "{}, with id: {}", target.getClass(), ResourceID.fromResource(target));
    postProcessDesired(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    return client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .create(target);
  }

  @Override
  protected R update(R actual, R target, P primary, Context context) {
    log.debug("Updating target resource with type: {}, with id: {}", target.getClass(),
        ResourceID.fromResource(target));
    postProcessDesired(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    return client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .replace(target);
  }

  @Override
  public Optional<EventSource> eventSource(EventSourceContext<P> context) {
    if (informerEventSource != null) {
      return Optional.of(informerEventSource);
    }
    var informerConfig = initInformerConfiguration(context);
    informerEventSource = new InformerEventSource(informerConfig, context);
    return Optional.of(informerEventSource);
  }

  private InformerConfiguration<R, P> initInformerConfiguration(EventSourceContext<P> context) {
    PrimaryResourcesRetriever<R> associatedPrimaries =
        (this instanceof PrimaryResourcesRetriever) ? (PrimaryResourcesRetriever<R>) this
            : getDefaultPrimaryResourcesRetriever();

    AssociatedSecondaryResourceIdentifier<P> associatedSecondary =
        (this instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<P>) this
            : getDefaultAssociatedSecondaryResourceIdentifier();

    return InformerConfiguration.from(context, resourceType())
        .withPrimaryResourcesRetriever(associatedPrimaries)
        .withAssociatedSecondaryResourceIdentifier(associatedSecondary)
        .build();
  }

  protected AssociatedSecondaryResourceIdentifier<P> getDefaultAssociatedSecondaryResourceIdentifier() {
    return (r) -> ResourceID.fromResource(r);
  }

  protected PrimaryResourcesRetriever<R> getDefaultPrimaryResourcesRetriever() {
    return Mappers.fromOwnerReference();
  }

  public KubernetesDependentResource<R, P> setInformerEventSource(
      InformerEventSource<R, P> informerEventSource) {
    this.informerEventSource = informerEventSource;
    return this;
  }

  @Override
  public void delete(P primary, Context context) {
    if (explicitDelete) {
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


  public KubernetesDependentResource<R, P> setExplicitDelete(boolean explicitDelete) {
    this.explicitDelete = explicitDelete;
    return this;
  }

  public boolean isExplicitDelete() {
    return explicitDelete;
  }

  public boolean isOwned() {
    return owned;
  }

  public KubernetesDependentResource<R, P> setOwned(boolean owned) {
    this.owned = owned;
    return this;
  }
}
