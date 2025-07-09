package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.event.EventSourceRetriever;
import io.javaoperatorsdk.operator.processing.event.NoEventSourceForClassException;

public class DefaultCacheAware<P extends HasMetadata> implements CacheAware<P> {

  protected final Controller<P> controller;
  protected final P primaryResource;

  public DefaultCacheAware(Controller<P> controller, P primaryResource) {
    this.controller = controller;
    this.primaryResource = primaryResource;
  }

  @Override
  public <T> Set<T> getSecondaryResources(Class<T> expectedType) {
    return getSecondaryResourcesAsStream(expectedType).collect(Collectors.toSet());
  }

  @Override
  public <R> Stream<R> getSecondaryResourcesAsStream(Class<R> expectedType) {
    return controller.getEventSourceManager().getEventSourcesFor(expectedType).stream()
        .map(es -> es.getSecondaryResources(primaryResource))
        .flatMap(Set::stream);
  }

  @Override
  public <T> Optional<T> getSecondaryResource(Class<T> expectedType, String eventSourceName) {
    try {
      return controller
          .getEventSourceManager()
          .getEventSourceFor(expectedType, eventSourceName)
          .getSecondaryResource(primaryResource);
    } catch (NoEventSourceForClassException e) {
      /*
       * If a workflow has an activation condition there can be event sources which are only
       * registered if the activation condition holds, but to provide a consistent API we return an
       * Optional instead of throwing an exception.
       *
       * Note that not only the resource which has an activation condition might not be registered
       * but dependents which depend on it.
       */
      if (eventSourceName == null && controller.workflowContainsDependentForType(expectedType)) {
        return Optional.empty();
      } else {
        throw e;
      }
    }
  }

  @Override
  public EventSourceRetriever<P> eventSourceRetriever() {
    return controller.getEventSourceManager();
  }

  @Override
  public ControllerConfiguration<P> getControllerConfiguration() {
    return controller.getConfiguration();
  }

  @Override
  public P getPrimaryResource() {
    return primaryResource;
  }

  @Override
  public IndexedResourceCache<P> getPrimaryCache() {
    return controller.getEventSourceManager().getControllerEventSource();
  }
}
