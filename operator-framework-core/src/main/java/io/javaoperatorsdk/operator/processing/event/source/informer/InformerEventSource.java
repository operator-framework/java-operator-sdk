package io.javaoperatorsdk.operator.processing.event.source.informer;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.EventHandler;
import io.javaoperatorsdk.operator.processing.event.source.ResourceCache;

public class InformerEventSource<T extends HasMetadata, P extends HasMetadata>
    extends ManagedInformerEventSource<T, P, InformerConfiguration<T, P>>
    implements ResourceCache<T>, ResourceEventHandler<T> {

  private final InformerConfiguration<T, P> configuration;

  public InformerEventSource(InformerConfiguration<T, P> configuration,
      EventSourceContext<P> context) {
    super(context.getClient().resources(configuration.getResourceClass()), configuration);
    this.configuration = configuration;
  }

  public InformerEventSource(InformerConfiguration<T, P> configuration,
      KubernetesClient client) {
    super(client.resources(configuration.getResourceClass()), configuration);
    this.configuration = configuration;
  }

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

    if (oldObject.getMetadata().getResourceVersion()
        .equals(newObject.getMetadata().getResourceVersion())) {
      return;
    }
    propagateEvent(newObject);
  }

  @Override
  public void onDelete(T t, boolean b) {
    propagateEvent(t);
  }

  private void propagateEvent(T object) {
    var primaryResourceIdSet =
        configuration.getPrimaryResourcesRetriever().associatedPrimaryResources(object);
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
      final EventHandler eventHandler = getEventHandler();
      if (eventHandler != null) {
        eventHandler.handleEvent(event);
      }
    });
  }

  @Override
  public void start() {
    manager().start();
  }

  @Override
  public void stop() {
    manager().stop();
  }

  /**
   * Retrieves the informed resource associated with the specified primary resource as defined by
   * the function provided when this InformerEventSource was created
   * 
   * @param resource the primary resource we want to retrieve the associated resource for
   * @return the informed resource associated with the specified primary resource
   */
  @Override
  public Optional<T> getAssociated(P resource) {
    final var id = configuration.getAssociatedResourceIdentifier().associatedSecondaryID(resource);
    return get(id);
  }

  @Override
  public Stream<T> list(String namespace, Predicate<T> predicate) {
    return manager().list(namespace, predicate);
  }
}
