package com.github.containersolutions.operator.api;

import com.github.containersolutions.operator.processing.event.Event;
import com.github.containersolutions.operator.processing.event.EventProducerManager;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public interface Context<T extends CustomResource> {

    RetryInfo retryInfo();

    EventProducerManager getEventManager();

    List<Event> getEvents();
}
