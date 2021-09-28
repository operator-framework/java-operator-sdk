package io.javaoperatorsdk.operator.processing.event.internal;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Store;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;

public class InformerEventSource<T extends HasMetadata> extends AbstractEventSource {

  private final SharedInformer<T> sharedInformer;
  private final ResourceToRelatedCustomResourceUIDMapper<T> mapper;
  private final CustomResourceToRelatedResourceMapper<T> reverseMapper;
  private final boolean skipUpdateEventPropagationIfNoChange;

  public InformerEventSource(SharedInformer<T> sharedInformer,
      ResourceToRelatedCustomResourceUIDMapper<T> mapper) {
    this(sharedInformer, mapper, null, true);
  }

  public InformerEventSource(KubernetesClient client, Class<T> type,
      ResourceToRelatedCustomResourceUIDMapper<T> mapper) {
    this(client, type, mapper, false);
  }

  InformerEventSource(KubernetesClient client, Class<T> type,
      ResourceToRelatedCustomResourceUIDMapper<T> mapper,
      boolean skipUpdateEventPropagationIfNoChange) {
    this(client.informers().sharedIndexInformerFor(type, 0), mapper, null,
        skipUpdateEventPropagationIfNoChange);
  }

  public InformerEventSource(SharedInformer<T> sharedInformer,
      ResourceToRelatedCustomResourceUIDMapper<T> mapper,
      CustomResourceToRelatedResourceMapper<T> reverseMapper,
      boolean skipUpdateEventPropagationIfNoChange) {
    this.sharedInformer = sharedInformer;
    this.mapper = mapper;
    this.skipUpdateEventPropagationIfNoChange = skipUpdateEventPropagationIfNoChange;

    this.reverseMapper = Objects.requireNonNullElseGet(reverseMapper, () -> cr -> {
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
    var uids = mapper.map(object);
    if (uids.isEmpty()) {
      return;
    }
    uids.forEach(uid -> {
      InformerEvent event = new InformerEvent(uid, this, action, object, oldObject);
      this.eventHandler.handleEvent(event);
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
   * the {@link CustomResourceToRelatedResourceMapper} provided when this InformerEventSource was
   * created
   * 
   * @param resource the primary resource we want to retrieve the associated resource for
   * @return the informed resource associated with the specified primary resource
   */
  public T getAssociated(HasMetadata resource) {
    return reverseMapper.associatedWith(resource);
  }


  public SharedInformer<T> getSharedInformer() {
    return sharedInformer;
  }

  @FunctionalInterface
  public interface ResourceToRelatedCustomResourceUIDMapper<T> {
    Set<String> map(T resource);
  }

  @FunctionalInterface
  public interface CustomResourceToRelatedResourceMapper<T> {
    T associatedWith(HasMetadata cr);
  }
}
