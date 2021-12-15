package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.javaoperatorsdk.operator.api.config.Cloner;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.source.AssociatedSecondaryRetriever;
import io.javaoperatorsdk.operator.processing.event.source.InformerResourceCache;
import io.javaoperatorsdk.operator.processing.event.source.PrimaryResourcesRetriever;
import io.javaoperatorsdk.operator.processing.event.source.controller.ResourceCache;

public class InformerEventSource<T extends HasMetadata, P extends HasMetadata>
    extends AbstractEventSource<P> {

  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);

  private final SharedInformer<T> sharedInformer;
  private final PrimaryResourcesRetriever<T, P> associatedPrimaries;
  private final AssociatedSecondaryRetriever<T, P> associatedWith;
  private final boolean skipUpdateEventPropagationIfNoChange;
  private final ResourceCache<T> cache;

  public InformerEventSource(SharedInformer<T> sharedInformer,
      PrimaryResourcesRetriever<T, P> associatedPrimaries, Cloner cloner) {
    this(sharedInformer, associatedPrimaries, null, true, cloner);
  }

  public InformerEventSource(KubernetesClient client, Class<T> type,
      PrimaryResourcesRetriever<T, P> associatedPrimaries, Cloner cloner) {
    this(client, type, associatedPrimaries, false, cloner);
  }

  InformerEventSource(KubernetesClient client, Class<T> type,
      PrimaryResourcesRetriever<T, P> associatedPrimaries,
      boolean skipUpdateEventPropagationIfNoChange, Cloner cloner) {
    this(client.informers().sharedIndexInformerFor(type, 0), associatedPrimaries, null,
        skipUpdateEventPropagationIfNoChange, cloner);
  }

  public InformerEventSource(SharedInformer<T> sharedInformer,
      PrimaryResourcesRetriever<T, P> associatedPrimaries,
      AssociatedSecondaryRetriever<T, P> associatedWith,
      boolean skipUpdateEventPropagationIfNoChange,
      Cloner cloner) {
    super(sharedInformer.getApiTypeClass());
    this.sharedInformer = sharedInformer;
    this.associatedPrimaries = Objects.requireNonNull(associatedPrimaries,
        () -> "Must specify a PrimaryResourcesRetriever for InformerEventSource for "
            + sharedInformer.getApiTypeClass());
    this.skipUpdateEventPropagationIfNoChange = skipUpdateEventPropagationIfNoChange;
    if (sharedInformer.isRunning()) {
      log.warn(
          "Informer is already running on event source creation, this is not desirable and may " +
              "lead to non deterministic behavior.");
    }
    this.cache = new InformerResourceCache<>(sharedInformer, cloner);

    this.associatedWith = Objects.requireNonNullElseGet(associatedWith,
        () -> (cr, registry) -> cache.get(ResourceID.fromResource(cr)).orElse(null));

    sharedInformer.addEventHandler(new ResourceEventHandler<>() {
      @Override
      public void onAdd(T t) {
        propagateEvent(t);
      }

      @Override
      public void onUpdate(T oldObject, T newObject) {
        if (InformerEventSource.this.skipUpdateEventPropagationIfNoChange &&
            oldObject.getMetadata().getResourceVersion()
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
    var primaryResourceIdSet =
        associatedPrimaries.associatedPrimaryResources(object, getEventRegistry());
    if (primaryResourceIdSet.isEmpty()) {
      return;
    }
    primaryResourceIdSet.forEach(resourceId -> {
      Event event = new Event(resourceId);
      /*
       * In fabric8 client for certain cases informers can be created on in a way that they are
       * automatically started, what would cause a NullPointerException here, since an event might
       * be received between creation and registration.
       */
      final var eventHandler = getEventHandler();
      if (eventHandler != null) {
        eventHandler.handleEvent(event);
      }
    });
  }

  @Override
  public void start() {
    sharedInformer.run();
  }

  @Override
  public void stop() {
    sharedInformer.close();
  }

  public ResourceCache<T> getCache() {
    return cache;
  }

  /**
   * Retrieves the informed resource associated with the specified primary resource as defined by
   * the function provided when this InformerEventSource was created
   * 
   * @param resource the primary resource we want to retrieve the associated resource for
   * @return the informed resource associated with the specified primary resource
   */
  public T getAssociated(P resource) {
    return associatedWith.associatedSecondary(resource, getEventRegistry());
  }

}
