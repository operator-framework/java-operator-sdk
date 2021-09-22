package io.javaoperatorsdk.operator.processing.event.internal;

import java.util.Optional;
import java.util.function.Predicate;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.DefaultEvent;
import io.javaoperatorsdk.operator.processing.event.EventSource;

public class InformerEvent<T> extends DefaultEvent {

  private Action action;
  private T resource;
  private T oldResource;

  public InformerEvent(String relatedCustomResourceUid, EventSource eventSource, Action action,
      T resource,
      T oldResource) {
    this(new UIDMatchingPredicate(relatedCustomResourceUid), eventSource, action, resource,
        oldResource);

  }

  public InformerEvent(Predicate<CustomResource> customResourcesSelector, EventSource eventSource,
      Action action,
      T resource, T oldResource) {
    super(customResourcesSelector, eventSource);
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

  public Action getAction() {
    return action;
  }

  public enum Action {
    ADD, UPDATE, DELETE
  }
}
