package io.javaoperatorsdk.operator.processing.event.source.inbound;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.CachingEventSource;

public class CachingInboundEventSource<T> extends CachingEventSource<T> {

  public void handleResourceEvent(T resource, ResourceID relatedResourceID) {
    super.handleEvent(resource, relatedResourceID);
  }

  public void handleResourceDeleteEvent(ResourceID resourceID) {
    super.handleDelete(resourceID);
  }
}
