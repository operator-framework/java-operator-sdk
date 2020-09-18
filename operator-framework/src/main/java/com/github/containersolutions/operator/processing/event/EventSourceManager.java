package com.github.containersolutions.operator.processing.event;

import java.util.List;

public interface EventSourceManager {

    void registerEventProducer(String customResourceUid, EventSource eventSource);

    void deRegisterEventProducer(String customResourceUid, EventSource eventSource);

    List<EventSource> getRegisteredEventSources(String customResource);

}
