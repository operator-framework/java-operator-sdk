package io.javaoperatorsdk.operator.processing.dependent.waitfor;

@FunctionalInterface
public interface Condition<R> {

  boolean isFulfilled(R resource);

}
