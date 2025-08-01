package io.javaoperatorsdk.operator.processing.event;

import java.time.LocalDateTime;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.expectation.Expectation;

public class ExpectationHolder<P extends HasMetadata> {

  private LocalDateTime expectationCreationTime;
  private Expectation<P> expectation;

  public ExpectationHolder(LocalDateTime expectationCreationTime, Expectation<P> expectation) {
    this.expectationCreationTime = expectationCreationTime;
    this.expectation = expectation;
  }

  public LocalDateTime getExpectationCreationTime() {
    return expectationCreationTime;
  }

  public void setExpectationCreationTime(LocalDateTime expectationCreationTime) {
    this.expectationCreationTime = expectationCreationTime;
  }

  public Expectation<?> getExpectation() {
    return expectation;
  }

  public void setExpectation(Expectation<P> expectation) {
    this.expectation = expectation;
  }

  public boolean isTimedOut() {
    return expectationCreationTime.plus(expectation.timeout()).isBefore(LocalDateTime.now());
  }
}
