package io.javaoperatorsdk.operator.processing.dependent.external;

public class PollingDependentResourceConfig {

  public static final long DEFAULT_POLLING_PERIOD = 700;

  private long pollingPeriod = DEFAULT_POLLING_PERIOD;

  public PollingDependentResourceConfig() {}

  public PollingDependentResourceConfig(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
  }

  public PollingDependentResourceConfig setPollingPeriod(long pollingPeriod) {
    this.pollingPeriod = pollingPeriod;
    return this;
  }

  public long getPollingPeriod() {
    return pollingPeriod;
  }
}
