package io.javaoperatorsdk.operator.processing.event.rate;

import java.time.LocalDateTime;

class RateState {

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
