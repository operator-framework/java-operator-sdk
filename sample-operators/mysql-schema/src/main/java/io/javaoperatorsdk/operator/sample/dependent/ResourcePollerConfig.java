package io.javaoperatorsdk.operator.sample.dependent;

import io.javaoperatorsdk.operator.sample.MySQLDbConfig;

public class ResourcePollerConfig {

  private int pollPeriod;
  private MySQLDbConfig mySQLDbConfig;

  public ResourcePollerConfig() {}

  public ResourcePollerConfig(int pollPeriod, MySQLDbConfig mySQLDbConfig) {
    initWith(pollPeriod, mySQLDbConfig);
  }

  void initWith(int pollPeriod, MySQLDbConfig mySQLDbConfig) {
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
