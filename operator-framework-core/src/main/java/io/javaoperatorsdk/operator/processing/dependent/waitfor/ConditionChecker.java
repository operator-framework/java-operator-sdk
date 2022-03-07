package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

import static java.lang.Thread.sleep;

public class ConditionChecker<R> {

  public static final Duration DEFAULT_POLLING_INTERVAL = Duration.ofSeconds(1);
  public static final Duration DEFAULT_TIMEOUT = Duration.ZERO;
  private static final int MIN_THREAD_SLEEP = 25;

  private Duration pollingInterval;
  private Duration timeout;
  private ConditionNotFulfilledHandler<?> conditionNotFulfilledHandler;
  private Condition<R> condition;

  public static <T> ConditionChecker<T> checker() {
    return new ConditionChecker<>();
  }

  public ConditionChecker() {
    this(DEFAULT_POLLING_INTERVAL, DEFAULT_TIMEOUT, UpdateControl::noUpdate);
  }

  public ConditionChecker(Duration pollingInterval, Duration timeout,
      ConditionNotFulfilledHandler conditionNotFulfilledHandler) {
    this.pollingInterval = pollingInterval;
    this.timeout = timeout;
    this.conditionNotFulfilledHandler = conditionNotFulfilledHandler;
  }

  public <P extends HasMetadata> void check(DependentResource<R, P> resource, P primary) {
    check(() -> resource.getResource(primary));
  }

  public void check(Supplier<Optional<R>> supplier) {
    checkSetup();
    Optional<R> resource = supplier.get();
    if (timeout.isNegative() || timeout.isZero()) {
      if (resource.isPresent() && condition.isFulfilled(resource.get())) {
        return;
      } else {
        handleConditionNotMet();
      }
    }
    var deadline = Instant.now().plus(timeout.toMillis(), ChronoUnit.MILLIS);
    while (Instant.now().isBefore(deadline)) {
      resource = supplier.get();
      if (resource.isPresent() && condition.isFulfilled(resource.get())) {
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

  private void checkSetup() {
    Objects.requireNonNull(conditionNotFulfilledHandler, "ConditionNotFulfilledHandler is not set");
    Objects.requireNonNull(condition, "Condition is not set");
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

  public ConditionChecker<R> withPollingInterval(Duration pollingInterval) {
    this.pollingInterval = pollingInterval;
    return this;
  }

  public ConditionChecker<R> withTimeout(Duration timeout) {
    this.timeout = timeout;
    return this;
  }

  public ConditionChecker<R> withConditionNotFulfilledHandler(
      ConditionNotFulfilledHandler conditionNotFulfilledHandler) {
    this.conditionNotFulfilledHandler = conditionNotFulfilledHandler;
    return this;
  }

  public ConditionChecker<R> withCondition(Condition<R> condition) {
    this.condition = condition;
    return this;
  }
}
