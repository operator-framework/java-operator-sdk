package io.javaoperatorsdk.operator.processing.retry;

@FunctionalInterface
public interface Retry {

  RetryExecution initExecution();
}
