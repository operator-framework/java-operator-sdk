package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.CustomResourceID;
import io.javaoperatorsdk.operator.processing.event.DefaultEvent;

public class InformerEvent<T extends HasMetadata> extends DefaultEvent {

  private final ResourceAction action;
  private final T resource;
  private final T oldResource;

  public InformerEvent(ResourceAction action,
      T resource, T oldResource) {
    super(CustomResourceID.fromResource(resource));
    this.action = action;
    this.resource = resource;
    this.oldResource = oldResource;
  }

  public T getResource() {
    return resource;
  }

  public Optional<T> getOldResource() {
    return Optional.ofNullable(oldResource);
  }

  public ResourceAction getAction() {
    return action;
  }

}
