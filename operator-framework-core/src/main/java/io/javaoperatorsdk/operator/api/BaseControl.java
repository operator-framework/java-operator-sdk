package io.javaoperatorsdk.operator.api;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public abstract class BaseControl<T extends BaseControl> {

  private Long scheduleDelay = null;

  public T rescheduleAfter(long delay) {
    this.scheduleDelay = delay;
    return (T) this;
  }

  public T rescheduleAfter(long delay, TimeUnit timeUnit) {
    return rescheduleAfter(timeUnit.toMillis(delay));
  }

  public Optional<Long> getScheduleDelay() {
    return Optional.ofNullable(scheduleDelay);
  }
}
