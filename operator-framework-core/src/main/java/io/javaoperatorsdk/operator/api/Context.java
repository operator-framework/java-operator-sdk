package io.javaoperatorsdk.operator.api;

import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventList;

public interface Context<T extends CustomResource> {

  EventList getEvents();

  Optional<RetryInfo> getRetryInfo();
}
