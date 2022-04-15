package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.MultiResourceOwner;

public class DefaultContext<P extends HasMetadata> implements Context<P> {

  private RetryInfo retryInfo;
  private final Controller<P> controller;
  private final P primaryResource;
  private final ControllerConfiguration<P> controllerConfiguration;
  private final ManagedDependentResourceContext managedDependentResourceContext;

  public DefaultContext(RetryInfo retryInfo, Controller<P> controller, P primaryResource) {
    this.retryInfo = retryInfo;
    this.controller = controller;
    this.primaryResource = primaryResource;
    this.controllerConfiguration = controller.getConfiguration();
    this.managedDependentResourceContext = new ManagedDependentResourceContext();
  }

  @Override
  public Optional<RetryInfo> getRetryInfo() {
    return Optional.ofNullable(retryInfo);
  }

  @Override
  public <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName) {
    return controller.getEventSourceManager()
        .getResourceEventSourceFor(expectedType, eventSourceName)
        .getSecondaryResource(primaryResource);
  }

  @Override
  public <T> List<T> getSecondaryResources(Class<T> expectedType, String eventSourceName) {
    var eventSource = controller.getEventSourceManager()
        .getResourceEventSourceFor(expectedType, eventSourceName);
    if (eventSource instanceof MultiResourceOwner) {
      return ((MultiResourceOwner<T, P>) eventSource).getSecondaryResources(primaryResource);
    } else {
      return eventSource.getSecondaryResource(primaryResource).map(List::of)
          .orElse(Collections.EMPTY_LIST);
    }
  }

  @Override
  public ControllerConfiguration<P> getControllerConfiguration() {
    return controllerConfiguration;
  }

  public ManagedDependentResourceContext managedDependentResourceContext() {
    return managedDependentResourceContext;
  }

  public DefaultContext<P> setRetryInfo(RetryInfo retryInfo) {
    this.retryInfo = retryInfo;
    return this;
  }
}
