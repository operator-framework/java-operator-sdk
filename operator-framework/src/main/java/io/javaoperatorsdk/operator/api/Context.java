package io.javaoperatorsdk.operator.api;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.processing.event.EventList;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

public interface Context<T extends CustomResource> {

  EventSourceManager getEventSourceManager();

  EventList getEvents();
}
