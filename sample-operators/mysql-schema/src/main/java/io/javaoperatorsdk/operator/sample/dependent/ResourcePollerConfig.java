package io.javaoperatorsdk.operator.sample.dependent;

import io.javaoperatorsdk.operator.sample.MySQLDbConfig;

public class ResourcePollerConfig {

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
