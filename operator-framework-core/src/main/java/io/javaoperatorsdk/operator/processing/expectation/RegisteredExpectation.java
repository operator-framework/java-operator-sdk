package io.javaoperatorsdk.operator.processing.expectation;

import java.time.Duration;
import java.time.LocalDateTime;

import io.fabric8.kubernetes.api.model.HasMetadata;

record RegisteredExpectation<P extends HasMetadata>(
    LocalDateTime registeredAt, Duration timeout, Expectation<P> expectation) {

  public boolean isTimedOut() {
    return LocalDateTime.now().isAfter(registeredAt.plus(timeout));
  }
}
