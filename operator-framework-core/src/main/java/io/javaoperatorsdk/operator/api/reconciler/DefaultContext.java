package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.source.DependentResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;

public class DefaultContext<P extends HasMetadata> implements Context<P> {

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
  public EventSourceRegistry<P> getEventSourceRegistry() {
    return controller.getEventSourceRegistry();
  }

  @Override
  public <T extends HasMetadata> T getSecondaryResource(Class<T> expectedType,
      String... qualifier) {
    final var eventSource = (DependentResourceEventSource<T, P>) getEventSourceRegistry()
        .getResourceEventSourceFor(expectedType, qualifier);
    return eventSource == null ? null : eventSource.getAssociated(primaryResource);
  }
}
