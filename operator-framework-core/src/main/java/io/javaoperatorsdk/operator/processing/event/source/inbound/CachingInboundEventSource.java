package io.javaoperatorsdk.operator.processing.event.source.inbound;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.IDMapper;

public class CachingInboundEventSource<R, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<R, P> {

  public CachingInboundEventSource(Class<R> resourceClass, IDMapper<R> idProvider) {
    super(resourceClass, idProvider);
  }

  public void handleResourceEvent(R resource, ResourceID primaryID) {
    super.handleEvent(resource, primaryID);
  }

  public void handleResourceDeleteEvent(ResourceID primaryID,String resourceID) {
    super.handleDelete(primaryID,resourceID);
  }

}
