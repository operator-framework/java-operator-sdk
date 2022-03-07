package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

import static java.lang.Thread.sleep;

public class DefaultWaiter<R, P extends HasMetadata> implements Waiter<R, P> {

  public static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofSeconds(3);
  public static final Duration DEFAULT_TIMEOUT = Duration.ZERO;
  public static final int MIN_THREAD_SLEEP = 25;

  private Duration pollingInterval;
  private Duration timeout;
  private ConditionNotFulfilledHandler conditionNotFulfilledHandler;

  public DefaultWaiter() {
    this(DEFAULT_POLLING_INTERVAL, DEFAULT_TIMEOUT, null);
  }

  public DefaultWaiter(Duration timeout) {
    this(DEFAULT_POLLING_INTERVAL, timeout);
  }

  public DefaultWaiter(Duration pollingInterval, Duration timeout) {
    this(pollingInterval, timeout, null);
  }

  public DefaultWaiter(Duration pollingInterval, Duration timeout,
      ConditionNotFulfilledHandler conditionNotFulfilledHandler) {
    this.pollingInterval = pollingInterval;
    this.timeout = timeout;
    this.conditionNotFulfilledHandler = conditionNotFulfilledHandler;
  }

  @Override
  public void waitFor(DependentResource<R, P> resource, P primary, Condition<R, P> condition) {
    waitFor(() -> resource.getResource(primary), condition);
  }

  @Override
  public void waitFor(Supplier<Optional<R>> supplier, Condition<R, P> condition) {
    Optional<R> resource = supplier.get();
    if (timeout.isNegative() || timeout.isZero()) {
      if (meetsCondition(resource, condition)) {
        return;
      } else {
        handleConditionNotMet();
      }
    }
    var deadline = Instant.now().plus(timeout.toMillis(), ChronoUnit.MILLIS);
    while (Instant.now().isBefore(deadline)) {
      resource = supplier.get();
      if (meetsCondition(resource, condition)) {
        return;
      } else {
        var timeLeft = Duration.between(Instant.now(), deadline);
        if (timeLeft.isZero() || timeLeft.isNegative()) {
          handleConditionNotMet();
        } else {
          sleepUntilNextPoll(timeLeft);
        }
      }
    }
    handleConditionNotMet();
  }

  private boolean meetsCondition(Optional<R> resource, Condition<R, P> condition) {
    return resource.isPresent() && condition.isFulfilled(resource.get());
  }

  private void sleepUntilNextPoll(Duration timeLeft) {
    try {
      sleep(Math.max(MIN_THREAD_SLEEP, Math.min(pollingInterval.toMillis(), timeLeft.toMillis())));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Thread interrupted.", e);
    }
  }

  private void handleConditionNotMet() {
    throw new ConditionNotFulfilledException(conditionNotFulfilledHandler);
  }

  public DefaultWaiter<R, P> setPollingInterval(Duration pollingInterval) {
    this.pollingInterval = pollingInterval;
    return this;
  }

  public DefaultWaiter<R, P> setTimeout(Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  public DefaultWaiter<R, P> setConditionNotMetHandler(
      ConditionNotFulfilledHandler conditionNotFulfilledHandler) {
    this.conditionNotFulfilledHandler = conditionNotFulfilledHandler;
    return this;
  }

}
