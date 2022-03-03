package io.javaoperatorsdk.operator.processing.dependent.dependson;

public interface Waiter<R> {

  void awaitFor(R resource);

}
