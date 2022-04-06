package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.cache.Store;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryResourceIdentifier;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

public class InformerEventSource<T extends HasMetadata, P extends HasMetadata>
    extends AbstractResourceEventSource<P, T> implements ResourceCache<T> {

  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);

  private final SharedInformer<T> sharedInformer;
  private final PrimaryResourcesRetriever<T> secondaryToPrimaryResourcesIdSet;
  private final AssociatedSecondaryResourceIdentifier<P> associatedWith;
  private final boolean skipUpdateEventPropagationIfNoChange;

  public InformerEventSource(
      SharedInformer<T> sharedInformer,
      PrimaryResourcesRetriever<T> resourceToTargetResourceIDSet) {
    this(sharedInformer, resourceToTargetResourceIDSet, null, true);
  }

  public InformerEventSource(
      KubernetesClient client,
      Class<T> type,
      PrimaryResourcesRetriever<T> resourceToTargetResourceIDSet) {
    this(client, type, resourceToTargetResourceIDSet, false);
  }

  public InformerEventSource(
      KubernetesClient client,
      Class<T> type,
      PrimaryResourcesRetriever<T> resourceToTargetResourceIDSet,
      AssociatedSecondaryResourceIdentifier<P> associatedWith,
      boolean skipUpdateEventPropagationIfNoChange) {
    this(
        client.informers().sharedIndexInformerFor(type, 0),
        resourceToTargetResourceIDSet,
        associatedWith,
        skipUpdateEventPropagationIfNoChange);
  }

  InformerEventSource(
      KubernetesClient client,
      Class<T> type,
      PrimaryResourcesRetriever<T> resourceToTargetResourceIDSet,
      boolean skipUpdateEventPropagationIfNoChange) {
    this(
        client.informers().sharedIndexInformerFor(type, 0),
        resourceToTargetResourceIDSet,
        null,
        skipUpdateEventPropagationIfNoChange);
  }

  public InformerEventSource(
      SharedInformer<T> sharedInformer,
      PrimaryResourcesRetriever<T> resourceToTargetResourceIDSet,
      AssociatedSecondaryResourceIdentifier<P> associatedWith,
      boolean skipUpdateEventPropagationIfNoChange) {
    super(sharedInformer.getApiTypeClass());
    this.sharedInformer = sharedInformer;
    this.secondaryToPrimaryResourcesIdSet = resourceToTargetResourceIDSet;
    this.skipUpdateEventPropagationIfNoChange = skipUpdateEventPropagationIfNoChange;
    if (sharedInformer.isRunning()) {
      log.warn(
          "Informer is already running on event source creation, this is not desirable and may "
              + "lead to non deterministic behavior.");
    }

    this.associatedWith =
        Objects.requireNonNullElseGet(associatedWith, () -> ResourceID::fromResource);

    sharedInformer.addEventHandler(
        new ResourceEventHandler<>() {
          @Override
          public void onAdd(T t) {
            propagateEvent(t);
          }

          @Override
          public void onUpdate(T oldObject, T newObject) {
            if (newObject == null) {
              // this is a fix for this potential issue with informer:
              // https://github.com/java-operator-sdk/java-operator-sdk/issues/830
              propagateEvent(oldObject);
              return;
            }

            if (InformerEventSource.this.skipUpdateEventPropagationIfNoChange
                && oldObject
                    .getMetadata()
                    .getResourceVersion()
                    .equals(newObject.getMetadata().getResourceVersion())) {
              return;
            }
            propagateEvent(newObject);
          }

          @Override
          public void onDelete(T t, boolean b) {
            propagateEvent(t);
          }
        });
  }

  private void propagateEvent(T object) {
    var primaryResourceIdSet = secondaryToPrimaryResourcesIdSet.associatedPrimaryResources(object);
    if (primaryResourceIdSet.isEmpty()) {
      return;
    }
    primaryResourceIdSet.forEach(
        resourceId -> {
          Event event = new Event(resourceId);
          /*
           * In fabric8 client for certain cases informers can be created on in a way that they are
           * automatically started, what would cause a NullPointerException here, since an event
           * might be received between creation and registration.
           */
          final EventHandler eventHandler = getEventHandler();
          if (eventHandler != null) {
            eventHandler.handleEvent(event);
          }
        });
  }

  @Override
  public void start() {
    try {
      sharedInformer.run();
    } catch (Exception e) {
      ReconcilerUtils.handleKubernetesClientException(e,
          HasMetadata.getFullResourceName(sharedInformer.getApiTypeClass()));
      throw e;
    }
  }

  @Override
  public void stop() {
    sharedInformer.close();
  }

  private Store<T> getStore() {
    return sharedInformer.getStore();
  }

  /**
   * Retrieves the informed resource associated with the specified primary resource as defined by
   * the function provided when this InformerEventSource was created
   *
   * @param resource the primary resource we want to retrieve the associated resource for
   * @return the informed resource associated with the specified primary resource
   */
  public Optional<T> getAssociated(P resource) {
    final var id = associatedWith.associatedSecondaryID(resource);
    return get(id);
  }

  public SharedInformer<T> getSharedInformer() {
    return sharedInformer;
  }

  @Override
  public Optional<T> get(ResourceID resourceID) {
    return Optional.ofNullable(
        sharedInformer
            .getStore()
            .getByKey(
                io.fabric8.kubernetes.client.informers.cache.Cache.namespaceKeyFunc(
                    resourceID.getNamespace().orElse(null), resourceID.getName())));
  }

  @Override
  public Stream<T> list(Predicate<T> predicate) {
    return getStore().list().stream().filter(predicate);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    return getStore().list().stream()
        .filter(v -> namespace.equals(v.getMetadata().getNamespace()) && predicate.test(v));
  }

  @Override
  public Stream<ResourceID> keys() {
    return getStore().listKeys().stream().map(Mappers::fromString);
  }
}
