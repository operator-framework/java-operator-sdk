package io.javaoperatorsdk.operator.sample.dependent;

import java.time.Duration;

import io.javaoperatorsdk.operator.sample.MySQLDbConfig;

public class ResourcePollerConfig {

  private final Duration pollPeriod;
  private final MySQLDbConfig mySQLDbConfig;

  public ResourcePollerConfig(Duration pollPeriod, MySQLDbConfig mySQLDbConfig) {
    this.pollPeriod = pollPeriod;
    this.mySQLDbConfig = mySQLDbConfig;
  }

  public Duration getPollPeriod() {
    return pollPeriod;
  }

  public MySQLDbConfig getMySQLDbConfig() {
    return mySQLDbConfig;
  }
}
