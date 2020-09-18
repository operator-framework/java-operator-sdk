package com.github.containersolutions.operator.processing.event;

import com.github.containersolutions.operator.processing.event.source.EventSource;

public interface EventHandler {

    void handleEvent(Event event);

}
