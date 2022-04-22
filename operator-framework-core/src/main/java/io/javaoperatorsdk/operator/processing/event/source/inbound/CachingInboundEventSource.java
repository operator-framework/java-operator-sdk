package io.javaoperatorsdk.operator.processing.event.source.inbound;

import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.source.IDMapper;

public class CachingInboundEventSource<R, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<R, P> {

  public CachingInboundEventSource(Class<R> resourceClass, IDMapper<R> idProvider) {
    super(resourceClass, idProvider);
  }

  public void handleResourceEvent(ResourceID primaryID, Set<R> resources) {
    super.handleResources(primaryID, resources);
  }

  public void handleResourceEvent(ResourceID primaryID, R resource) {
    super.handleResources(primaryID, resource);
  }

  public void handleResourceDeleteEvent(ResourceID primaryID, String resourceID) {
    super.handleDelete(primaryID, Set.of(resourceID));
  }

}
