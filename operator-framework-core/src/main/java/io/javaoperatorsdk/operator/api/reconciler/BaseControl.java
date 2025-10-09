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

  private Long scheduleDelay = null;

  public T rescheduleAfter(long delay) {
    rescheduleAfter(Duration.ofMillis(delay));
    return (T) this;
  }

  public T rescheduleAfter(Duration delay) {
    this.scheduleDelay = delay.toMillis();
    return (T) this;
  }

  public T rescheduleAfter(long delay, TimeUnit timeUnit) {
    return rescheduleAfter(timeUnit.toMillis(delay));
  }

  public Optional<Long> getScheduleDelay() {
    return Optional.ofNullable(scheduleDelay);
  }
}
