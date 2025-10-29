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

import java.util.Optional;

public class GenericRetryExecution implements RetryExecution {

  private final GenericRetry genericRetry;

  private int lastAttemptIndex = 0;
  private long currentInterval;

  public GenericRetryExecution(GenericRetry genericRetry) {
    this.genericRetry = genericRetry;
    this.currentInterval = genericRetry.getInitialInterval();
  }

  public Optional<Long> nextDelay() {
    if (genericRetry.getMaxAttempts() > -1 && lastAttemptIndex >= genericRetry.getMaxAttempts()) {
      return Optional.empty();
    }
    if (lastAttemptIndex > 1) {
      currentInterval = (long) (currentInterval * genericRetry.getIntervalMultiplier());
      if (genericRetry.getMaxInterval() > -1 && currentInterval > genericRetry.getMaxInterval()) {
        currentInterval = genericRetry.getMaxInterval();
      }
    }
    lastAttemptIndex++;
    return Optional.of(currentInterval);
  }

  @Override
  public boolean isLastAttempt() {
    return genericRetry.getMaxAttempts() > -1 && lastAttemptIndex >= genericRetry.getMaxAttempts();
  }

  @Override
  public int getAttemptCount() {
    return lastAttemptIndex;
  }
}
