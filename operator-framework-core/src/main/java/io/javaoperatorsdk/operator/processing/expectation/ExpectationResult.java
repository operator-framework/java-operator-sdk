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

import io.fabric8.kubernetes.api.model.HasMetadata;

public record ExpectationResult<P extends HasMetadata>(
    Expectation<P> expectation, ExpectationStatus status) {

  public boolean isFulfilled() {
    return status == ExpectationStatus.FULFILLED;
  }

  public boolean isTimedOut() {
    return status == ExpectationStatus.TIMED_OUT;
  }

  public boolean isExpectationPresent() {
    return expectation != null;
  }

  public boolean isNotPresentOrFulfilled() {
    return !isExpectationPresent() || isFulfilled();
  }

  public String name() {
    return expectation.name();
  }
}
