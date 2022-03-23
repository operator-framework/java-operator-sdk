package io.javaoperatorsdk.operator.processing.event.source.inbound;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.ExternalResourceCachingEventSource;
import io.javaoperatorsdk.operator.processing.event.ObjectKey;

public class CachingInboundEventSource<T, P extends HasMetadata>
    extends ExternalResourceCachingEventSource<T, P> {

  public CachingInboundEventSource(Class<T> resourceClass) {
    super(resourceClass);
  }

  public void handleResourceEvent(T resource, ObjectKey relatedObjectKey) {
    super.handleEvent(resource, relatedObjectKey);
  }

  public void handleResourceDeleteEvent(ObjectKey objectKey) {
    super.handleDelete(objectKey);
  }
}
