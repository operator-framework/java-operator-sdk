package io.javaoperatorsdk.operator.processing.event.source;

import io.javaoperatorsdk.operator.processing.event.ResourceID;

public interface EventFilter<T> {

  boolean accept(T newValue, T oldValue, ResourceID relatedResourceID);

}
