package io.javaoperatorsdk.operator.api;

public interface Event {

  String getRelatedCustomResourceUid();

  EventSource getEventSource();
}
