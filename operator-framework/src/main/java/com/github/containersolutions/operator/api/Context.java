package com.github.containersolutions.operator.api;

import com.github.containersolutions.operator.processing.event.Event;
import com.github.containersolutions.operator.processing.event.source.EventSourceManager;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.List;

public interface Context<T extends CustomResource> {

    EventSourceManager getEventSourceManager();

    List<Event> getEvents();

}
