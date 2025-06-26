package io.javaoperatorsdk.operator.api.reconciler.expectation;

import java.time.Duration;
import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.HasMetadata;

public class ExpectationAdapter<P extends HasMetadata> extends AbstractExpectation<P> {

  private final BiPredicate<P, ExpectationContext<P>> expectation;

  public ExpectationAdapter(BiPredicate<P, ExpectationContext<P>> expectation, Duration timeout) {
    super(timeout);
    this.expectation = expectation;
  }

  @Override
  public boolean isFulfilled(P primary, ExpectationContext<P> context) {
    return expectation.test(primary, context);
  }
}
