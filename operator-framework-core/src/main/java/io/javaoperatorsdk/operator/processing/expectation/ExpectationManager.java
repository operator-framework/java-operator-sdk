/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.processing.expectation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Experimental;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

import static io.javaoperatorsdk.operator.api.reconciler.Experimental.API_MIGHT_CHANGE;

@Experimental(API_MIGHT_CHANGE)
public class ExpectationManager<P extends HasMetadata> {

  protected final ConcurrentHashMap<ResourceID, RegisteredExpectation<P>> registeredExpectations =
      new ConcurrentHashMap<>();

  public void setExpectation(P primary, Duration timeout, Expectation<P> expectation) {
    registeredExpectations.put(
        ResourceID.fromResource(primary),
        new RegisteredExpectation<>(LocalDateTime.now(), timeout, expectation));
  }

  /**
   * Checks on expectation with provided name. Return the expectation result. If the result of
   * expectation is fulfilled, the expectation is automatically removed;
   */
  public ExpectationResult<P> checkExpectation(
      String expectationName, P primary, Context<P> context) {
    var resourceID = ResourceID.fromResource(primary);
    var exp = registeredExpectations.get(ResourceID.fromResource(primary));
    if (exp != null && expectationName.equals(exp.expectation().name())) {
      return checkExpectation(exp, resourceID, primary, context);
    } else {
      return checkExpectation(null, resourceID, primary, context);
    }
  }

  /**
   * Checks if actual expectation is fulfilled. Return the expectation result. If the result of
   * expectation is fulfilled, the expectation is automatically removed;
   */
  public ExpectationResult<P> checkExpectation(P primary, Context<P> context) {
    var resourceID = ResourceID.fromResource(primary);
    var exp = registeredExpectations.get(ResourceID.fromResource(primary));
    return checkExpectation(exp, resourceID, primary, context);
  }

  private ExpectationResult<P> checkExpectation(
      RegisteredExpectation<P> exp, ResourceID resourceID, P primary, Context<P> context) {
    if (exp == null) {
      return new ExpectationResult<>(null, null);
    }
    if (exp.expectation().isFulfilled(primary, context)) {
      registeredExpectations.remove(resourceID);
      return new ExpectationResult<>(exp.expectation(), ExpectationStatus.FULFILLED);
    } else if (exp.isTimedOut()) {
      // we don't remove the expectation so user knows about it's state
      return new ExpectationResult<>(exp.expectation(), ExpectationStatus.TIMED_OUT);
    } else {
      return new ExpectationResult<>(exp.expectation(), ExpectationStatus.NOT_YET_FULFILLED);
    }
  }

  /*
   * Returns true if there is an expectation for the primary resource, but it is not yet fulfilled
   * neither timed out.
   * The intention behind is that you can exit reconciliation early with a simple check
   * if true.
   * */
  public boolean ongoingExpectationPresent(P primary, Context<P> context) {
    var exp = registeredExpectations.get(ResourceID.fromResource(primary));
    if (exp == null) {
      return false;
    }
    return !exp.isTimedOut() && !exp.expectation().isFulfilled(primary, context);
  }

  public boolean isExpectationPresent(P primary) {
    return registeredExpectations.containsKey(ResourceID.fromResource(primary));
  }

  public boolean isExpectationPresent(String name, P primary) {
    var exp = registeredExpectations.get(ResourceID.fromResource(primary));
    return exp != null && name.equals(exp.expectation().name());
  }

  public Optional<Expectation<P>> getExpectation(P primary) {
    var regExp = registeredExpectations.get(ResourceID.fromResource(primary));
    return Optional.ofNullable(regExp).map(RegisteredExpectation::expectation);
  }

  public Optional<String> getExpectationName(P primary) {
    return getExpectation(primary).map(Expectation::name);
  }

  public void removeExpectation(P primary) {
    registeredExpectations.remove(ResourceID.fromResource(primary));
  }

  public void cleanup(P primary) {
    registeredExpectations.remove(ResourceID.fromResource(primary));
  }
}
