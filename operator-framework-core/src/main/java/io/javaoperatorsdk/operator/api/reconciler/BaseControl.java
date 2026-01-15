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
package io.javaoperatorsdk.operator.api.reconciler;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public abstract class BaseControl<T extends BaseControl<T>> {

  public static final Long INSTANT_RESCHEDULE = 0L;

  private Long scheduleDelay = null;

  /**
   * Schedules a reconciliation to occur after the specified delay in milliseconds.
   *
   * @param delay the delay in milliseconds after which to reschedule
   * @return this control instance for fluent chaining
   */
  public T rescheduleAfter(long delay) {
    rescheduleAfter(Duration.ofMillis(delay));
    return (T) this;
  }

  /**
   * Schedules a reconciliation to occur after the specified delay.
   *
   * @param delay the {@link Duration} after which to reschedule
   * @return this control instance for fluent chaining
   */
  public T rescheduleAfter(Duration delay) {
    this.scheduleDelay = delay.toMillis();
    return (T) this;
  }

  /**
   * Schedules a reconciliation to occur after the specified delay using the given time unit.
   *
   * @param delay the delay value
   * @param timeUnit the time unit of the delay
   * @return this control instance for fluent chaining
   */
  public T rescheduleAfter(long delay, TimeUnit timeUnit) {
    return rescheduleAfter(timeUnit.toMillis(delay));
  }

  /**
   * Schedules an instant reconciliation. The reconciliation will be triggered as soon as possible.
   *
   * @return this control instance for fluent chaining
   */
  public T reschedule() {
    this.scheduleDelay = INSTANT_RESCHEDULE;
    return (T) this;
  }

  public Optional<Long> getScheduleDelay() {
    return Optional.ofNullable(scheduleDelay);
  }
}
