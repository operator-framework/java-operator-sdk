package io.javaoperatorsdk.operator.api.reconciler.expectation;

import java.time.Duration;

import io.fabric8.kubernetes.api.model.HasMetadata;

public abstract class AbstractExpectation<P extends HasMetadata> implements Expectation<P> {

  protected final Duration timeout;

  protected AbstractExpectation(Duration timeout) {
    this.timeout = timeout;
  }

  @Override
  public abstract boolean isFulfilled(P primary, ExpectationContext<P> context);

  @Override
  public Duration timeout() {
    return timeout;
  }
}
