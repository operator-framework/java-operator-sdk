package io.javaoperatorsdk.operator.processing.event.internal;

import java.io.IOException;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.cache.Store;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;

public class InformerEventSource<T extends HasMetadata> extends AbstractEventSource {

  private final SharedInformer<T> sharedInformer;
  private final ResourceToRelatedCustomResourceUIDMapper<T> mapper;
  private final boolean skipUpdateEventPropagationIfNoChange;

  public InformerEventSource(SharedInformer<T> sharedInformer,
      ResourceToRelatedCustomResourceUIDMapper<T> mapper) {
    this(sharedInformer, mapper, true);
  }

  InformerEventSource(KubernetesClient client, Class<T> type,
      ResourceToRelatedCustomResourceUIDMapper<T> mapper) {
    this(client, type, mapper, false);
  }

  InformerEventSource(KubernetesClient client, Class<T> type,
      ResourceToRelatedCustomResourceUIDMapper<T> mapper,
      boolean skipUpdateEventPropagationIfNoChange) {
    this(client.informers().sharedIndexInformerFor(type, 0), mapper,
        skipUpdateEventPropagationIfNoChange);
  }

  public InformerEventSource(SharedInformer<T> sharedInformer,
      ResourceToRelatedCustomResourceUIDMapper<T> mapper,
      boolean skipUpdateEventPropagationIfNoChange) {
    this.sharedInformer = sharedInformer;
    this.mapper = mapper;
    this.skipUpdateEventPropagationIfNoChange = skipUpdateEventPropagationIfNoChange;

    sharedInformer.addEventHandler(new ResourceEventHandler<T>() {
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
    var uid = mapper.map(object);
    if (uid.isEmpty()) {
      return;
    }
    InformerEvent event = new InformerEvent(uid.get(), this, action, object, oldObject);
    this.eventHandler.handleEvent(event);
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

  public SharedInformer<T> getSharedInformer() {
    return sharedInformer;
  }

  public interface ResourceToRelatedCustomResourceUIDMapper<T> {
    // in case cannot map to the related CR uid, skip the event processing
    Optional<String> map(T resource);
  }

}
