package io.javaoperatorsdk.operator.processing.cache;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface ExistenceCheckHandler {

  boolean resourceExists(ResourceID resourceID);

}
