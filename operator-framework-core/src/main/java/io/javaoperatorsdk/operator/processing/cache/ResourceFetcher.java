package io.javaoperatorsdk.operator.processing.cache;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface ResourceFetcher<R> {

  R fetchResource(ResourceID resourceID);

}
