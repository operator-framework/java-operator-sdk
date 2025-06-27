package io.javaoperatorsdk.operator.api.reconciler.expectation;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ExpectationResult<P extends HasMetadata> {

  private ExpectationStatus status;

  private Expectation<P> expectation;

  public ExpectationResult(ExpectationStatus status) {
    this.status = status;
  }

  public ExpectationStatus getStatus() {
    return status;
  }

  public Expectation<P> getExpectation() {
    return expectation;
  }
}
