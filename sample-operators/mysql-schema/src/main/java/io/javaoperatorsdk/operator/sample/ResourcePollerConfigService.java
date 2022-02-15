package io.javaoperatorsdk.operator.sample;

import java.util.Optional;

import io.javaoperatorsdk.operator.api.config.dependent.DependentResourceConfigService;

public class ResourcePollerConfigService implements DependentResourceConfigService {

  private Integer pollPeriod;
  private MySQLDbConfig mySQLDbConfig;

  public ResourcePollerConfigService(MySQLDbConfig mySQLDbConfig) {
    this.mySQLDbConfig = mySQLDbConfig;
  }

  public ResourcePollerConfigService(int pollPeriod, MySQLDbConfig mySQLDbConfig) {
    this.pollPeriod = pollPeriod;
    this.mySQLDbConfig = mySQLDbConfig;
  }

  public Optional<Integer> getPollPeriod() {
    return Optional.ofNullable(pollPeriod);
  }

  public MySQLDbConfig getMySQLDbConfig() {
    return mySQLDbConfig;
  }
}
