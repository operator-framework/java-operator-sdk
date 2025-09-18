package io.javaoperatorsdk.operator.processing.expectation;

import io.fabric8.kubernetes.api.model.HasMetadata;

public record ExpectationResult<P extends HasMetadata>(
    Expectation<P> expectation, ExpectationStatus status) {

  public boolean isFulfilled() {
    return status == ExpectationStatus.FULFILLED;
  }

  public boolean isTimedOut() {
    return status == ExpectationStatus.TIMED_OUT;
  }

  public String name() {
    return expectation.name();
  }
}
