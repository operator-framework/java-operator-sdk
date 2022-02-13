package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.dependent.matcher.PatchRecordMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.dependent.KubernetesDependentResourceConfiguration;
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
    extends AbstractDependentResource<R, P> implements KubernetesClientAware {

  private static final Logger log = LoggerFactory.getLogger(KubernetesDependentResource.class);

  protected KubernetesClient client;
  private boolean explicitDelete = false;
  private boolean owned = true;
  private InformerEventSource<R, P> informerEventSource;
  private DesiredSupplier<R, P> desiredSupplier = null;
  private Class<R> resourceType = null;
  private AssociatedSecondaryResourceIdentifier<P> associatedSecondaryResourceIdentifier =
      ResourceID::fromResource;
  private PrimaryResourcesRetriever<R> primaryResourcesRetriever = Mappers.fromOwnerReference();
  private PatchRecordMatcher<R,P> patchRecordMatcher = new PatchRecordMatcher<>();

  public KubernetesDependentResource() {
    this(null);
  }

  public KubernetesDependentResource(KubernetesClient client) {
    this.client = client;
  }

  public KubernetesDependentResource(
      KubernetesClient client, Class<R> resourceType, DesiredSupplier<R, P> desiredSupplier) {
    this.client = client;
    this.resourceType = resourceType;
    this.desiredSupplier = desiredSupplier;
  }

  public KubernetesDependentResource(
      Class<R> resourceType, DesiredSupplier<R, P> desiredSupplier) {
    this(null, resourceType, desiredSupplier);
  }

  // todo builder and/or factory methods
  public void initWithConfiguration(KubernetesDependentResourceConfiguration<R, P> config) {
    this.owned = config.isOwned();
    InformerConfiguration<R, P> ic =
        InformerConfiguration.from(config.getConfigurationService(), resourceType())
            .withLabelSelector(config.getLabelSelector())
            .withNamespaces(config.getNamespaces())
            .withPrimaryResourcesRetriever(getPrimaryResourcesRetriever())
            .withAssociatedSecondaryResourceIdentifier(getAssociatedSecondaryResourceIdentifier())
            .build();
    informerEventSource = new InformerEventSource<>(ic, client);
  }

  protected void beforeCreateOrUpdate(R desired, P primary) {
    if (owned) {
      desired.addOwnerReference(primary);
    }
  }

  @Override
  protected boolean match(R actual, R desired, Context context) {
    return patchRecordMatcher.match(actual,desired,context);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected R create(R target, P primary, Context context) {
    log.debug("Creating target resource with type: " +
        "{}, with id: {}", target.getClass(), ResourceID.fromResource(target));
    beforeCreateOrUpdate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();

    var created = client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .create(target);
    patchRecordMatcher.onCreated(target,created);
    return created;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected R update(R actual, R target, P primary, Context context) {
    log.debug("Updating target resource with type: {}, with id: {}", target.getClass(),
        ResourceID.fromResource(target));
    beforeCreateOrUpdate(target, primary);
    Class<R> targetClass = (Class<R>) target.getClass();
    return client.resources(targetClass).inNamespace(target.getMetadata().getNamespace())
        .replace(target);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public Optional<EventSource> eventSource(EventSourceContext<P> context) {
    if (informerEventSource != null) {
      return Optional.of(informerEventSource);
    }
    var informerConfig = initDefaultInformerConfiguration(context);
    informerEventSource = new InformerEventSource(informerConfig, context);
    return Optional.of(informerEventSource);
  }

  @SuppressWarnings("unchecked")
  private InformerConfiguration<R, P> initDefaultInformerConfiguration(
      EventSourceContext<P> context) {
    return InformerConfiguration.from(context, resourceType())
        .withPrimaryResourcesRetriever(getPrimaryResourcesRetriever())
        .withAssociatedSecondaryResourceIdentifier(getAssociatedSecondaryResourceIdentifier())
        .build();
  }


  protected PrimaryResourcesRetriever<R> getPrimaryResourcesRetriever() {
    return (this instanceof PrimaryResourcesRetriever) ? (PrimaryResourcesRetriever<R>) this
        : primaryResourcesRetriever;
  }

  protected AssociatedSecondaryResourceIdentifier<P> getAssociatedSecondaryResourceIdentifier() {
    return (this instanceof AssociatedSecondaryResourceIdentifier)
        ? (AssociatedSecondaryResourceIdentifier<P>) this
        : associatedSecondaryResourceIdentifier;
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

  @Override
  public void setKubernetesClient(KubernetesClient client) {
    this.client = client;
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

  @Override
  public Class<R> resourceType() {
    if (resourceType != null) {
      return resourceType;
    } else {
      return super.resourceType();
    }
  }

  @Override
  protected R desired(P primary, Context context) {
    return desiredSupplier.getDesired(primary, context);
  }

  public KubernetesDependentResource<R, P> setAssociatedSecondaryResourceIdentifier(
      AssociatedSecondaryResourceIdentifier<P> associatedSecondaryResourceIdentifier) {
    this.associatedSecondaryResourceIdentifier = associatedSecondaryResourceIdentifier;
    return this;
  }

  public KubernetesDependentResource<R, P> setPrimaryResourcesRetriever(
      PrimaryResourcesRetriever<R> primaryResourcesRetriever) {
    this.primaryResourcesRetriever = primaryResourcesRetriever;
    return this;
  }

  public KubernetesDependentResource<R, P> setDesiredSupplier(
      DesiredSupplier<R, P> desiredSupplier) {
    this.desiredSupplier = desiredSupplier;
    return this;
  }

  public KubernetesDependentResource<R, P> setResourceType(Class<R> resourceType) {
    this.resourceType = resourceType;
    return this;
  }
}
