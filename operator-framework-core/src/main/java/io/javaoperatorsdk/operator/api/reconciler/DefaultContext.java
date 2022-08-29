package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DefaultManagedDependentResourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

public class DefaultContext<P extends HasMetadata> implements Context<P> {

  private RetryInfo retryInfo;
  private final Controller<P> controller;
  private final P primaryResource;
  private final ControllerConfiguration<P> controllerConfiguration;
  private final DefaultManagedDependentResourceContext defaultManagedDependentResourceContext;

  public DefaultContext(RetryInfo retryInfo, Controller<P> controller, P primaryResource) {
    this.retryInfo = retryInfo;
    this.controller = controller;
    this.primaryResource = primaryResource;
    this.controllerConfiguration = controller.getConfiguration();
    this.defaultManagedDependentResourceContext = new DefaultManagedDependentResourceContext();
  }

  @Override
  public Optional<RetryInfo> getRetryInfo() {
    return Optional.ofNullable(retryInfo);
  }

  @Override
  public <T> Set<T> getSecondaryResources(Class<T> expectedType) {
    return eventSources().getResourceEventSourcesFor(expectedType).stream()
        .map(es -> es.getSecondaryResources(primaryResource))
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
  }

  private EventSourceManager<P> eventSources() {
    return controller.getEventSourceManager();
  }

  @Override
  public <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName) {
    return eventSources()
        .getResourceEventSourceFor(expectedType, eventSourceName)
        .getSecondaryResource(primaryResource);
  }

  @Override
  public ControllerConfiguration<P> getControllerConfiguration() {
    return controllerConfiguration;
  }

  @Override
  public ManagedDependentResourceContext managedDependentResourceContext() {
    return defaultManagedDependentResourceContext;
  }

  public DefaultContext<P> setRetryInfo(RetryInfo retryInfo) {
    this.retryInfo = retryInfo;
    return this;
  }
}
