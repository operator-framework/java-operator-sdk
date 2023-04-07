package io.javaoperatorsdk.operator.performance;

import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;

public class CounterEventSource extends AbstractEventSource {

    private final String namespace;

    public CounterEventSource(String namespace) {
        this.namespace = namespace;
    }

    public void generateEvent(int counterNr) {
        getEventHandler().handleEvent(new Event(new ResourceID("counter-" + counterNr, namespace)));
    }
}
