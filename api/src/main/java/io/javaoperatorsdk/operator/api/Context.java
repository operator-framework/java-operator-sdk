package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import java.util.Optional;

public interface Context<T extends CustomResource> {

  EventSourceManager getEventSourceManager();

  EventList getEvents();

  Optional<RetryInfo> getRetryInfo();
}
