package io.javaoperatorsdk.operator.processing.event.source;

public interface ExternalIDMapper<R,T> {

    T getExternalID(R resource);

}
