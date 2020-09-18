package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.event.source.EventSource;
import io.fabric8.kubernetes.client.CustomResource;

public interface Event {

    String getRelatedCustomResourceUid();

    EventSource getEventSource();
}
