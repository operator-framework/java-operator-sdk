package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static io.javaoperatorsdk.operator.processing.dependent.waitfor.ConditionChecker.checker;

class ConditionCheckerTest {

  @Test
  void returnsIfDurationIsZeroAndConditionMet() {
    checker()
        .withTimeout(Duration.ZERO)
        .withCondition(r -> true)
        .check(() -> Optional.of(new TestCustomResource()));
  }

  @Test
  void throwsExceptionIfDurationZeroConditionNotFulfilled() {
    Assertions.assertThrows(
        ConditionNotFulfilledException.class,
        () -> new ConditionChecker<TestCustomResource>()
            .withTimeout(Duration.ZERO)
            .withCondition(r -> false)
            .check(() -> Optional.of(new TestCustomResource())));
  }

  @Test
  void throwsExceptionNoDurationIfResourceNotPresentWithinTimeout() {
    Assertions.assertThrows(
        ConditionNotFulfilledException.class,
        () -> new ConditionChecker<TestCustomResource>()
            .withTimeout(Duration.ZERO)
            .withCondition(r -> true)
            .check(Optional::empty));
  }

  @Test
  @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
  void waitsForTheConditionToFulfill() {
    new ConditionChecker<TestCustomResource>()
        .withTimeout(Duration.ofMillis(200))
        .withPollingInterval(Duration.ofMillis(50))
        .withCondition(r -> true)
        .check(() -> Optional.of(new TestCustomResource()));
  }

  @Test
  @Timeout(value = 230, unit = TimeUnit.MILLISECONDS)
  void throwsExceptionIfConditionNotFulfilledWithinTimeout() {
    Assertions.assertThrows(
        ConditionNotFulfilledException.class, () -> new ConditionChecker<TestCustomResource>()
            .withTimeout(Duration.ofMillis(200))
            .withPollingInterval(Duration.ofMillis(50))
            .withCondition(r -> false)
            .check(() -> Optional.of(new TestCustomResource())));

  }

  @Test
  @Timeout(value = 230, unit = TimeUnit.MILLISECONDS)
  void throwsExceptionIfResourceNotPresentWithinTimeout() {
    Assertions.assertThrows(
        ConditionNotFulfilledException.class, () -> new ConditionChecker<TestCustomResource>()
            .withTimeout(Duration.ofMillis(200))
            .withPollingInterval(Duration.ofMillis(50))
            .withCondition(r -> true)
            .check(() -> Optional.empty()));
  }
}
