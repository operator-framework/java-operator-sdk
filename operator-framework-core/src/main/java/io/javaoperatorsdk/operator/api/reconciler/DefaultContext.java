package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DefaultManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedWorkflowAndDependentResourceContext;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class DefaultContext<P extends HasMetadata> implements Context<P> {

  private RetryInfo retryInfo;
  private final Controller<P> controller;
  private final P primaryResource;
  private final ControllerConfiguration<P> controllerConfiguration;
  private final DefaultManagedWorkflowAndDependentResourceContext<P> defaultManagedDependentResourceContext;

  public DefaultContext(RetryInfo retryInfo, Controller<P> controller, P primaryResource) {
    this.retryInfo = retryInfo;
    this.controller = controller;
    this.primaryResource = primaryResource;
    this.controllerConfiguration = controller.getConfiguration();
    this.defaultManagedDependentResourceContext =
        new DefaultManagedWorkflowAndDependentResourceContext<>(controller, primaryResource, this);
  }

  @Override
  public Optional<RetryInfo> getRetryInfo() {
    return Optional.ofNullable(retryInfo);
  }

  @Override
  public <T> Set<T> getSecondaryResources(Class<T> expectedType) {
    return getSecondaryResourcesAsStream(expectedType).collect(Collectors.toSet());
  }

  @Override
  public IndexedResourceCache<P> getPrimaryCache() {
    return controller.getEventSourceManager().getControllerEventSource();
  }

  @Override
  public boolean isNextReconciliationImminent() {
    return controller.getEventProcessor()
        .isNextReconciliationImminent(ResourceID.fromResource(primaryResource));
  }

  @Override
  public <R> Stream<R> getSecondaryResourcesAsStream(Class<R> expectedType) {
    return controller.getEventSourceManager().getEventSourcesFor(expectedType).stream()
        .map(es -> es.getSecondaryResources(primaryResource))
        .flatMap(Set::stream);
  }

  @Override
  public <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName) {
    return controller
        .getEventSourceManager()
        .getEventSourceFor(expectedType, eventSourceName)
        .getSecondaryResource(primaryResource);
  }

  @Override
  public ControllerConfiguration<P> getControllerConfiguration() {
    return controllerConfiguration;
  }

  @Override
  public ManagedWorkflowAndDependentResourceContext managedWorkflowAndDependentResourceContext() {
    return defaultManagedDependentResourceContext;
  }

  @Override
  public EventSourceRetriever<P> eventSourceRetriever() {
    return controller.getEventSourceManager();
  }

  @Override
  public KubernetesClient getClient() {
    return controller.getClient();
  }

  @Override
  public ExecutorService getWorkflowExecutorService() {
    // note that this should be always received from executor service manager, so we are able to do
    // restarts.
    return controller.getExecutorServiceManager().workflowExecutorService();
  }

  public DefaultContext<P> setRetryInfo(RetryInfo retryInfo) {
    this.retryInfo = retryInfo;
    return this;
  }
}
