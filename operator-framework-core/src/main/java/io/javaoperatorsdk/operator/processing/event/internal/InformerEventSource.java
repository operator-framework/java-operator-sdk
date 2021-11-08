package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  private static final Logger log = LoggerFactory.getLogger(InformerEventSource.class);

  private final SharedInformer<T> sharedInformer;
  private final Function<T, Set<CustomResourceID>> resourceToCustomResourceIDSet;
  private final Function<HasMetadata, T> associatedWith;
  private final boolean skipUpdateEventPropagationIfNoChange;

  public InformerEventSource(SharedInformer<T> sharedInformer,
      Function<T, Set<CustomResourceID>> resourceToCustomResourceIDSet) {
    this(sharedInformer, resourceToCustomResourceIDSet, null, true);
  }

  public InformerEventSource(KubernetesClient client, Class<T> type,
      Function<T, Set<CustomResourceID>> resourceToCustomResourceIDSet) {
    this(client, type, resourceToCustomResourceIDSet, false);
  }

  InformerEventSource(KubernetesClient client, Class<T> type,
      Function<T, Set<CustomResourceID>> resourceToCustomResourceIDSet,
      boolean skipUpdateEventPropagationIfNoChange) {
    this(client.informers().sharedIndexInformerFor(type, 0), resourceToCustomResourceIDSet, null,
        skipUpdateEventPropagationIfNoChange);
  }

  public InformerEventSource(SharedInformer<T> sharedInformer,
      Function<T, Set<CustomResourceID>> resourceToCustomResourceIDSet,
      Function<HasMetadata, T> associatedWith,
      boolean skipUpdateEventPropagationIfNoChange) {
    this.sharedInformer = sharedInformer;
    this.resourceToCustomResourceIDSet = resourceToCustomResourceIDSet;
    this.skipUpdateEventPropagationIfNoChange = skipUpdateEventPropagationIfNoChange;
    if (sharedInformer.isRunning()) {
      log.warn(
          "Informer is already running on event source creation, this is not desirable and may " +
              "lead to non deterministic behavior.");
    }

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
    var uids = resourceToCustomResourceIDSet.apply(object);
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
