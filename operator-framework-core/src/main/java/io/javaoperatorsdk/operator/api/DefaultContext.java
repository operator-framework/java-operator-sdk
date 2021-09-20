package io.javaoperatorsdk.operator.api;

import java.util.Optional;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventList;

public class DefaultContext<T extends CustomResource> implements Context<T> {

  private final RetryInfo retryInfo;
  private final EventList events;

  public DefaultContext(EventList events, RetryInfo retryInfo) {
    this.retryInfo = retryInfo;
    this.events = events;
  }

  @Override
  public EventList getEvents() {
    return events;
  }

  @Override
  public Optional<RetryInfo> getRetryInfo() {
    return Optional.ofNullable(retryInfo);
  }
}
