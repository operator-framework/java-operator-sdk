package io.javaoperatorsdk.operator.processing.event.source.informer;

public class EventContext {

    private static final EventContext INFORMER_PRODUCER_CONTEXT = new EventContext(EventProducer.INFORMER);
    private static final EventContext MISSING_EVENT_HANDLER_PRODUCER_CONTEXT =
            new EventContext(EventProducer.MISSING_EVENT_HANDLER);

    public static EventContext informerEventProducerContext(){
        return INFORMER_PRODUCER_CONTEXT;
    }

    public static EventContext missingEventProducerContext(){
        return MISSING_EVENT_HANDLER_PRODUCER_CONTEXT;
    }

    private final EventProducer eventProducer;

    public EventContext(EventProducer eventProducer) {
        this.eventProducer = eventProducer;
    }

    public EventProducer getEventProducer() {
        return eventProducer;
    }
}
