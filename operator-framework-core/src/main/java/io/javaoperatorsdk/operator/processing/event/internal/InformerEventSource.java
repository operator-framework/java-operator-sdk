package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Store;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.Event;

public class InformerEventSource<T extends HasMetadata> extends AbstractEventSource {

  private final SharedInformer<T> sharedInformer;
  private final Function<T, Set<CustomResourceID>> resourceToUIDs;
  private final Function<HasMetadata, T> associatedWith;
  private final boolean skipUpdateEventPropagationIfNoChange;

  public InformerEventSource(SharedInformer<T> sharedInformer,
      Function<T, Set<CustomResourceID>> resourceToUIDs) {
    this(sharedInformer, resourceToUIDs, null, true);
  }

  public InformerEventSource(KubernetesClient client, Class<T> type,
      Function<T, Set<CustomResourceID>> resourceToUIDs) {
    this(client, type, resourceToUIDs, false);
  }

  InformerEventSource(KubernetesClient client, Class<T> type,
      Function<T, Set<CustomResourceID>> resourceToUIDs,
      boolean skipUpdateEventPropagationIfNoChange) {
    this(client.informers().sharedIndexInformerFor(type, 0), resourceToUIDs, null,
        skipUpdateEventPropagationIfNoChange);
  }

  public InformerEventSource(SharedInformer<T> sharedInformer,
      Function<T, Set<CustomResourceID>> resourceToUIDs,
      Function<HasMetadata, T> associatedWith,
      boolean skipUpdateEventPropagationIfNoChange) {
    this.sharedInformer = sharedInformer;
    this.resourceToUIDs = resourceToUIDs;
    this.skipUpdateEventPropagationIfNoChange = skipUpdateEventPropagationIfNoChange;

    this.associatedWith = Objects.requireNonNullElseGet(associatedWith, () -> cr -> {
      final var metadata = cr.getMetadata();
      return getStore().getByKey(Cache.namespaceKeyFunc(metadata.getNamespace(),
          metadata.getName()));
    });

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
    var uids = resourceToUIDs.apply(object);
    if (uids.isEmpty()) {
      return;
    }
    uids.forEach(uid -> {
      Event event = new Event(CustomResourceID.fromResource(object));
      /*
       * In fabric8 client for certain cases informers can be created on in a way that they are
       * automatically started, what would cause a NullPointerException here, since an event might
       * be received between creation and registration.
       */
      if (eventHandler != null) {
        this.eventHandler.handleEvent(event);
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

  public Store<T> getStore() {
    return sharedInformer.getStore();
  }

  /**
   * Retrieves the informed resource associated with the specified primary resource as defined by
   * the function provided when this InformerEventSource was created
   * 
   * @param resource the primary resource we want to retrieve the associated resource for
   * @return the informed resource associated with the specified primary resource
   */
  public T getAssociated(HasMetadata resource) {
    return associatedWith.apply(resource);
  }


  public SharedInformer<T> getSharedInformer() {
    return sharedInformer;
  }
}
