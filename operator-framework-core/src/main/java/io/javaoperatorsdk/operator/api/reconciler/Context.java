package io.javaoperatorsdk.operator.api.reconciler;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.processing.event.source.EventSourceRegistry;

public interface Context<P extends HasMetadata> {

  Optional<RetryInfo> getRetryInfo();

  EventSourceRegistry<P> getEventSourceRegistry();

  <T extends HasMetadata> T getSecondaryResource(Class<T> expectedType, String... qualifier);
}
