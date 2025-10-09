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

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinearRateLimiterTest {

  public static final Duration REFRESH_PERIOD = Duration.ofMillis(300);
  private RateState state;

  @BeforeEach
  void initState() {
    state = RateState.initialState();
  }

  @Test
  void acquirePermissionForNewResource() {
    var rl = new LinearRateLimiter(REFRESH_PERIOD, 2);
    var res = rl.isLimited(state);
    assertThat(res).isEmpty();
    res = rl.isLimited(state);
    assertThat(res).isEmpty();

    res = rl.isLimited(state);
    assertThat(res).isNotEmpty();
  }

  @Test
  void returnsMinimalDurationToAcquirePermission() {
    var rl = new LinearRateLimiter(REFRESH_PERIOD, 1);
    var res = rl.isLimited(state);
    assertThat(res).isEmpty();

    res = rl.isLimited(state);

    assertThat(res).isPresent();
    assertThat(res.get()).isLessThan(REFRESH_PERIOD);
  }

  @Test
  void resetsPeriodAfterLimit() throws InterruptedException {
    var rl = new LinearRateLimiter(REFRESH_PERIOD, 1);
    var res = rl.isLimited(state);
    assertThat(res).isEmpty();
    res = rl.isLimited(state);
    assertThat(res).isPresent();

    // sleep plus some slack
    Thread.sleep(REFRESH_PERIOD.toMillis() + REFRESH_PERIOD.toMillis() / 3);

    res = rl.isLimited(state);
    assertThat(res).isEmpty();
  }

  @Test
  void rateLimitCanBeTurnedOff() {
    var rl = new LinearRateLimiter(REFRESH_PERIOD, LinearRateLimiter.NO_LIMIT_PERIOD);

    var res = rl.isLimited(state);

    assertThat(res).isEmpty();
  }
}
