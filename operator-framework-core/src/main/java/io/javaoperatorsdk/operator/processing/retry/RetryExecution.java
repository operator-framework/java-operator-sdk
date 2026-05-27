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
package io.javaoperatorsdk.operator.processing.retry;

import java.time.Duration;
import java.util.Optional;

import io.javaoperatorsdk.operator.api.reconciler.RetryInfo;

public interface RetryExecution extends RetryInfo {

  /**
   * @return the time to wait until the next execution in milliseconds
   */
  Optional<Long> nextDelay();

  /**
   * Remaining time of the currently scheduled retry interval, i.e. the time until the previously
   * computed retry delay would elapse. Returns an empty {@link Optional} if no retry has been
   * scheduled yet (i.e. {@link #nextDelay()} has never been called) or if the deadline has already
   * passed.
   *
   * <p>Used to decide whether an event-driven failed reconciliation that lands well inside the
   * retry window should consume a retry attempt or simply be re-scheduled on the original deadline.
   */
  Optional<Duration> remainingDurationUntilNextRetry();
}
