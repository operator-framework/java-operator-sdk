package io.javaoperatorsdk.operator.sample;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfig;

public class ResourcePollerConfig implements DependentResourceConfig {

  private final int pollPeriod;
  private final MySQLDbConfig mySQLDbConfig;


  public ResourcePollerConfig(int pollPeriod, MySQLDbConfig mySQLDbConfig) {
    this.pollPeriod = pollPeriod;
    this.mySQLDbConfig = mySQLDbConfig;
  }

  public int getPollPeriod() {
    return pollPeriod;
  }

  public MySQLDbConfig getMySQLDbConfig() {
    return mySQLDbConfig;
  }
}
