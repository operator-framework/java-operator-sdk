package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.sample.simple.TestCustomResource;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWaiterTest {

  @Test
  void returnsIfDurationIsZeroAndConditionMet() {
    new DefaultWaiter<>()
        .setTimeout(Duration.ZERO)
        .waitFor(() -> Optional.of(new TestCustomResource()), r -> true);
  }

  @Test
  void throwsExceptionIfDurationZeroConditionNotFulfilled() {
    Assertions.assertThrows(
        ConditionNotFulfilledException.class,
        () -> new DefaultWaiter<>()
            .setTimeout(Duration.ZERO)
            .waitFor(() -> Optional.of(new TestCustomResource()), r -> false));
  }

  @Test
  void throwsExceptionNoDurationIfResourceNotPresentWithinTimeout() {

  }

  @Test
  void waitsForTheConditionToFulfill() {}

  @Test
  void throwsExceptionIfConditionNotFulfilledWithinTimeout() {}

  @Test
  void throwsExceptionIfResourceNotPresentWithinTimeout() {}
}
