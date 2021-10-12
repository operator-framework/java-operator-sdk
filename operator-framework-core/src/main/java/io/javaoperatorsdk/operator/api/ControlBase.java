package io.javaoperatorsdk.operator.api;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public abstract class ControlBase<T extends ControlBase> {

  private Long reScheduleDelay = null;

  public T withReSchedule(long delay) {
    this.reScheduleDelay = delay;
    return (T) this;
  }

  public T withReSchedule(long delay, TimeUnit timeUnit) {
    return withReSchedule(timeUnit.toMillis(delay));
  }

  public Optional<Long> getReScheduleDelay() {
    return Optional.ofNullable(reScheduleDelay);
  }
}
