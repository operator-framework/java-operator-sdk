package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;
import java.util.function.Function;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.Cache;

public class ResourceIDMatcherDiscriminator<R extends HasMetadata, P extends HasMetadata>
    implements ResourceDiscriminator<R, P> {


  private final String eventSourceName;
  private final Function<P, ResourceID> mapper;

  public ResourceIDMatcherDiscriminator(Function<P, ResourceID> mapper) {
    this(null, mapper);
  }

  public ResourceIDMatcherDiscriminator(String eventSourceName, Function<P, ResourceID> mapper) {
    this.eventSourceName = eventSourceName;
    this.mapper = mapper;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Optional<R> distinguish(Class<R> resource, P primary, Context<P> context) {
    var resourceID = mapper.apply(primary);
    if (eventSourceName != null) {
      return ((Cache<R>) context.eventSourceRetriever().getResourceEventSourceFor(resource,
          eventSourceName))
          .get(resourceID);
    } else {
      var eventSources = context.eventSourceRetriever().getResourceEventSourcesFor(resource);
      if (eventSources.size() == 1) {
        return ((Cache<R>) eventSources.get(0)).get(resourceID);
      } else {
        return context.getSecondaryResourcesAsStream(resource)
            .filter(resourceID::isSameResource)
            .findFirst();
      }
    }
  }
}
