package io.javaoperatorsdk.operator.processing.experimental;

import io.javaoperatorsdk.operator.processing.event.EventSource;

public interface DependentResource<T> extends EventSource {

    Status getStatus(String ownerResourceId);

    DependentResource<T> createOrUpdate(String ownerResourceId, T specs);



}
