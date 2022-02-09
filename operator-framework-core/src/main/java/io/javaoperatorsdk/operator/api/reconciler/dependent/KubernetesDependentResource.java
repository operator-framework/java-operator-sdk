package io.javaoperatorsdk.operator.api.reconciler.dependent;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.OperatorException;
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

// todo owned: owner reference setting
public class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
    extends AbstractDependentResource<R, P> {

  private KubernetesClient client;
  private boolean manageDelete;
  private InformerEventSource<R, P> informerEventSource;
  private DesiredSupplier<R, P> desiredSupplier = null;

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
  protected R desired(P primary, Context context) {
    if (desiredSupplier != null) {
      return desiredSupplier.getDesired(primary, context);
    } else {
      throw new OperatorException(
          "No DesiredSupplier provided. Either provide one or override this method");
    }
  }

  @Override
  protected boolean match(R actual, R target, Context context) {
    return ReconcilerUtils.specsEqual(actual, target);
  }

  @Override
  protected R create(R target, Context context) {
    // todo implement here https://github.com/java-operator-sdk/java-operator-sdk/issues/870
    return client.resource(target).createOrReplace();
  }

  @Override
  protected R update(R actual, R target, Context context) {
    // todo implement here https://github.com/java-operator-sdk/java-operator-sdk/issues/870
    // todo map annotation and labels ?
    return client.resource(target).createOrReplace();
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
            : Mappers.fromOwnerReference();

    AssociatedSecondaryResourceIdentifier<R> associatedSecondary =
        (this instanceof AssociatedSecondaryResourceIdentifier)
            ? (AssociatedSecondaryResourceIdentifier<R>) this
            : (r) -> ResourceID.fromResource(r);

    return InformerConfiguration.from(context, resourceType())
        .withPrimaryResourcesRetriever(associatedPrimaries)
        .withAssociatedSecondaryResourceIdentifier(
            (AssociatedSecondaryResourceIdentifier<P>) associatedSecondary)
        .build();
  }

  public KubernetesDependentResource<R, P> setInformerEventSource(
      InformerEventSource<R, P> informerEventSource) {
    this.informerEventSource = informerEventSource;
    return this;
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

  public KubernetesDependentResource<R, P> setDesiredSupplier(
      DesiredSupplier<R, P> desiredSupplier) {
    this.desiredSupplier = desiredSupplier;
    return this;
  }

  public KubernetesDependentResource<R, P> setManageDelete(boolean manageDelete) {
    this.manageDelete = manageDelete;
    return this;
  }

  public boolean isManageDelete() {
    return manageDelete;
  }

  public DesiredSupplier<R, P> getDesiredSupplier() {
    return desiredSupplier;
  }
}
