package io.javaoperatorsdk.operator.processing.event.internal;

import java.io.IOException;
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

public class InformerEventSource<T extends HasMetadata> extends AbstractEventSource {

  private final SharedInformer<T> sharedInformer;
  private final Function<T, Set<String>> resourceToUIDs;
  private final Function<HasMetadata, T> associatedWith;
  private final boolean skipUpdateEventPropagationIfNoChange;

  public InformerEventSource(SharedInformer<T> sharedInformer,
      Function<T, Set<String>> resourceToUIDs) {
    this(sharedInformer, resourceToUIDs, null, true);
  }

  public InformerEventSource(KubernetesClient client, Class<T> type,
      Function<T, Set<String>> resourceToUIDs) {
    this(client, type, resourceToUIDs, false);
  }

  InformerEventSource(KubernetesClient client, Class<T> type,
      Function<T, Set<String>> resourceToUIDs,
      boolean skipUpdateEventPropagationIfNoChange) {
    this(client.informers().sharedIndexInformerFor(type, 0), resourceToUIDs, null,
        skipUpdateEventPropagationIfNoChange);
  }

  public InformerEventSource(SharedInformer<T> sharedInformer,
      Function<T, Set<String>> resourceToUIDs,
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
        propagateEvent(InformerEvent.Action.ADD, t, null);
      }

      @Override
      public void onUpdate(T oldObject, T newObject) {
        if (InformerEventSource.this.skipUpdateEventPropagationIfNoChange &&
            oldObject.getMetadata().getResourceVersion()
                .equals(newObject.getMetadata().getResourceVersion())) {
          return;
        }
        propagateEvent(InformerEvent.Action.UPDATE, newObject, oldObject);
      }

      @Override
      public void onDelete(T t, boolean b) {
        propagateEvent(InformerEvent.Action.DELETE, t, null);
      }
    });
  }

  private void propagateEvent(InformerEvent.Action action, T object, T oldObject) {
    var uids = resourceToUIDs.apply(object);
    if (uids.isEmpty()) {
      return;
    }
    uids.forEach(uid -> {
      InformerEvent event = new InformerEvent(uid, this, action, object, oldObject);
      if (this.eventHandler != null) {
        this.eventHandler.handleEvent(event);
      }
    });
  }

  @Override
  public void start() {
    sharedInformer.run();
  }

  @Override
  public void close() throws IOException {
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
