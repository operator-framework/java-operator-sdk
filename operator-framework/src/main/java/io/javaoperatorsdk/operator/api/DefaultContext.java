package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventList;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;
import java.util.Optional;

public class DefaultContext<T extends CustomResource> implements Context<T> {

  private final RetryInfo retryInfo;
  private final EventList events;
  private final EventSourceManager eventSourceManager;

  public DefaultContext(
      EventSourceManager eventSourceManager, EventList events, RetryInfo retryInfo) {
    this.retryInfo = retryInfo;
    this.events = events;
    this.eventSourceManager = eventSourceManager;
  }

  @Override
  public EventSourceManager getEventSourceManager() {
    return eventSourceManager;
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
