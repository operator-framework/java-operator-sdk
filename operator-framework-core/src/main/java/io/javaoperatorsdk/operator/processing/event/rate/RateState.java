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
package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.LocalDateTime;

import io.javaoperatorsdk.operator.processing.event.rate.RateLimiter.RateLimitState;

class RateState implements RateLimitState {

  private LocalDateTime lastRefreshTime;
  private int count;

  public static RateState initialState() {
    return new RateState(LocalDateTime.now(), 0);
  }

  RateState(LocalDateTime lastRefreshTime, int count) {
    this.lastRefreshTime = lastRefreshTime;
    this.count = count;
  }

  public void increaseCount() {
    count = count + 1;
  }

  public void reset() {
    lastRefreshTime = LocalDateTime.now();
    count = 0;
  }

  public LocalDateTime getLastRefreshTime() {
    return lastRefreshTime;
  }

  public int getCount() {
    return count;
  }
}
