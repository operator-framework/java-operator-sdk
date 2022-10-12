package io.javaoperatorsdk.operator.sample.externalstate;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.processing.dependent.AbstractDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher.Result;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.event.source.ResourceEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.polling.PerResourcePollingEventSource.ResourceFetcher;
import io.javaoperatorsdk.operator.support.ExternalIDGenServiceMock;
import io.javaoperatorsdk.operator.support.ExternalResource;

import static io.javaoperatorsdk.operator.sample.externalstate.ExternalStateReconciler.ID_KEY;

public class ExternalResourceDependentResource extends
    AbstractDependentResource<ExternalResource, ExternalStateCustomResource>
    implements Creator<ExternalResource, ExternalStateCustomResource>,
    Updater<ExternalResource, ExternalStateCustomResource> {
  private final ExternalIDGenServiceMock externalService = ExternalIDGenServiceMock.getInstance();

  @Override
  public Class<ExternalResource> resourceType() {
    return ExternalResource.class;
  }

  @Override
  public Optional<ResourceEventSource<ExternalResource, ExternalStateCustomResource>> eventSource(
      EventSourceContext<ExternalStateCustomResource> context) {
    final var fetcher = new ResourceFetcher<ExternalResource, ExternalStateCustomResource>() {
      @Override
      public Set<ExternalResource> fetchResources(ExternalStateCustomResource primaryResource) {
        return context.getResourceEventSourceWhenStartedFor(ConfigMap.class, null)
            .thenApply(eventSource -> {
              final var configMap = eventSource.getSecondaryResource(primaryResource)
                  .orElseThrow();
              var id = configMap.getData().get(ID_KEY);
              var externalResource = externalService.read(id);
              return externalResource.map(Set::of).orElse(Collections.emptySet());
            })
            .toCompletableFuture().join();
      }
    };

    return Optional.of(new PerResourcePollingEventSource<>(fetcher,
        context.getPrimaryCache(), 300L, ExternalResource.class));
  }

  @Override
  protected ExternalResource desired(ExternalStateCustomResource primary,
      Context<ExternalStateCustomResource> context) {
    return new ExternalResource(primary.getSpec().getData());
  }

  @Override
  public ExternalResource create(ExternalResource desired, ExternalStateCustomResource primary,
      Context<ExternalStateCustomResource> context) {
    return externalService.create(desired);
  }

  @Override
  public ExternalResource update(ExternalResource actual, ExternalResource desired,
      ExternalStateCustomResource primary, Context<ExternalStateCustomResource> context) {
    return externalService.update(new ExternalResource(actual.getId(), desired.getData()));
  }

  @Override
  public Result<ExternalResource> match(ExternalResource actualResource,
      ExternalStateCustomResource primary, Context<ExternalStateCustomResource> context) {
    return Result.nonComputed(primary.getSpec().getData().equals(actualResource.getData()));
  }
}
