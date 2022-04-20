package io.javaoperatorsdk.operator.processing.event.source;

public interface IDProvider<R> {

    String getID(R resource);

}
