package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;

public class DefaultContext<P extends HasMetadata> implements Context {

  private final RetryInfo retryInfo;
  private final Controller<P> controller;
  private final P primaryResource;

  public DefaultContext(RetryInfo retryInfo, Controller<P> controller, P primaryResource) {
    this.retryInfo = retryInfo;
    this.controller = controller;
    this.primaryResource = primaryResource;
  }

  @Override
  public Optional<RetryInfo> getRetryInfo() {
    return Optional.ofNullable(retryInfo);
  }

  @Override
  public <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName) {
    final Optional<ResourceEventSource<P, T>> eventSource =
        controller.getEventSourceManager().getResourceEventSourceFor(expectedType, eventSourceName);
    return eventSource.isEmpty() ? Optional.empty()
        : eventSource.get().getAssociated(primaryResource);
  }
}
