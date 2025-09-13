package io.javaoperatorsdk.operator.processing.expectation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

public class ExpectationManager<P extends HasMetadata> {

  private final ConcurrentHashMap<ResourceID, RegisteredExpectation<P>> registeredExpectations =
      new ConcurrentHashMap<>();

  public void setExpectation(P primary, Expectation<P> expectation, Duration timeout) {
    registeredExpectations.put(
        ResourceID.fromResource(primary),
        new RegisteredExpectation<>(LocalDateTime.now(), timeout, expectation));
  }

  /**
   * Checks if provided expectation is fulfilled. Return the expectation result. If the result of
   * expectation is fulfilled or timeout, the expectation is automatically removed;
   */
  public Optional<ExpectationResult<P>> checkOnExpectation(P primary, Context<P> context) {
    var resourceID = ResourceID.fromResource(primary);
    var regExp = registeredExpectations.get(ResourceID.fromResource(primary));
    if (regExp == null) {
      return Optional.empty();
    }
    if (regExp.expectation().isFulfilled(primary, context)) {
      registeredExpectations.remove(resourceID);
      return Optional.of(
          new ExpectationResult<>(regExp.expectation(), ExpectationStatus.FULFILLED));
    } else if (regExp.isTimedOut()) {
      registeredExpectations.remove(resourceID);
      return Optional.of(
          new ExpectationResult<>(regExp.expectation(), ExpectationStatus.TIMED_OUT));
    } else {
      return Optional.of(
          new ExpectationResult<>(regExp.expectation(), ExpectationStatus.NOT_FULFILLED));
    }
  }

  public boolean isExpectationPresent(P primary) {
    return registeredExpectations.containsKey(ResourceID.fromResource(primary));
  }

  public Optional<Expectation<P>> getExpectation(P primary) {
    var regExp = registeredExpectations.get(ResourceID.fromResource(primary));
    return Optional.ofNullable(regExp).map(RegisteredExpectation::expectation);
  }

  public void cleanup(P primary) {
    registeredExpectations.remove(ResourceID.fromResource(primary));
  }
}
