package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.DefaultManagedDependentResourceContext;
import io.javaoperatorsdk.operator.api.reconciler.dependent.managed.ManagedDependentResourceContext;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;

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
  public <T> Set<T> getSecondaryResources(Class<T> resourceType) {
    return getSecondaryResourcesAsStream(resourceType).collect(Collectors.toSet());
  }

  @Override
  public <R> Stream<R> getSecondaryResourcesAsStream(Class<R> resourceType) {
    return controller.getEventSourceManager().getResourceEventSourcesFor(resourceType).stream()
        .map(es -> es.getSecondaryResources(primaryResource))
        .flatMap(Set::stream);
  }

  @Override
  public <T> Optional<T> getSecondaryResource(Class<T> resourceType, String eventSourceName) {
    return controller
        .getEventSourceManager()
        .getResourceEventSourceFor(resourceType, eventSourceName)
        .getSecondaryResource(primaryResource);
  }

  @Override
  public <R> Optional<R> getSecondaryResource(Class<R> resourceType,
      ResourceDiscriminator<R, P> discriminator) {
    return discriminator.distinguish(resourceType, primaryResource, this);
  }

  @Override
  public <R extends HasMetadata> Optional<R> getResource(Class<R> resourceType, String name,
      String namespace) {
    return controller
        .getEventSourceManager()
        .getResourceEventSourcesFor(resourceType).stream()
        .filter(InformerEventSource.class::isInstance)
        .map(es -> ((InformerEventSource<R, P>) es).get(new ResourceID(name, namespace)))
        .flatMap(Optional::stream)
        .findAny();
  }

  @Override
  public <R extends HasMetadata> Optional<R> getResource(Class<R> resourceType,
      String eventSourceName,
      String name, String namespace) {
    var es = controller
        .getEventSourceManager()
        .getResourceEventSourceFor(resourceType, eventSourceName);
    if (es instanceof InformerEventSource) {
      return ((InformerEventSource<R, P>) es).get(new ResourceID(name, namespace));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public ControllerConfiguration<P> getControllerConfiguration() {
    return controllerConfiguration;
  }

  @Override
  public ManagedDependentResourceContext managedDependentResourceContext() {
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
