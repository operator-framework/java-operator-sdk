package io.javaoperatorsdk.operator.api.reconciler;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.expectation.Expectation;
import io.javaoperatorsdk.operator.api.reconciler.expectation.ExpectationAdapter;
import io.javaoperatorsdk.operator.api.reconciler.expectation.ExpectationContext;

public abstract class BaseControl<T extends BaseControl<T, P>, P extends HasMetadata> {

  private Long scheduleDelay = null;
  private Expectation<P> expectation;

  public T rescheduleAfter(long delay) {
    rescheduleAfter(Duration.ofMillis(delay));
    return (T) this;
  }

  public T rescheduleAfter(Duration delay) {
    this.scheduleDelay = delay.toMillis();
    return (T) this;
  }

  public T rescheduleAfter(long delay, TimeUnit timeUnit) {
    return rescheduleAfter(timeUnit.toMillis(delay));
  }

  public Optional<Long> getScheduleDelay() {
    return Optional.ofNullable(scheduleDelay);
  }

  public void expect(Expectation<P> expectation) {
    this.expectation = expectation;
  }

  public void expect(BiPredicate<P, ExpectationContext<P>> expectation, Duration timeout) {
    this.expectation = new ExpectationAdapter<>(expectation, timeout);
  }

  public Expectation<P> getExpectation() {
    return expectation;
  }
}
