package io.javaoperatorsdk.operator.processing.event;

public abstract class AbstractEvent<T extends EventSource> implements Event<T> {

    private final String relatedCustomResourceUid;

    private final T eventSource;

    public AbstractEvent(String relatedCustomResourceUid, T eventSource) {
        this.relatedCustomResourceUid = relatedCustomResourceUid;
        this.eventSource = eventSource;
    }

    @Override
    public String getRelatedCustomResourceUid() {
        return relatedCustomResourceUid;
    }

    @Override
    public T getEventSource() {
        return eventSource;
    }

    @Override
    public String toString() {
        return "{ class=" + this.getClass().getName() +
                ", relatedCustomResourceUid=" + relatedCustomResourceUid +
                ", eventSource=" + eventSource + " }";

    }
}
