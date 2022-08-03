package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;

import static io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration.*;

public final class LeaderElectionConfigurationBuilder {

  private String leaseName;
  private String leaseNamespace;
  private Duration leaseDuration = LEASE_DURATION_DEFAULT_VALUE;
  private Duration renewDeadline = RENEW_DEADLINE_DEFAULT_VALUE;
  private Duration retryPeriod = RETRY_PERIOD_DEFAULT_VALUE;
  private String identity;

  public LeaderElectionConfigurationBuilder() {}

  public static LeaderElectionConfigurationBuilder aLeaderElectionConfiguration() {
    return new LeaderElectionConfigurationBuilder();
  }

  public LeaderElectionConfigurationBuilder withLeaseName(String leaseName) {
    this.leaseName = leaseName;
    return this;
  }

  public LeaderElectionConfigurationBuilder withLeaseNamespace(String leaseNamespace) {
    this.leaseNamespace = leaseNamespace;
    return this;
  }

  public LeaderElectionConfigurationBuilder withLeaseDuration(Duration leaseDuration) {
    this.leaseDuration = leaseDuration;
    return this;
  }

  public LeaderElectionConfigurationBuilder withRenewDeadline(Duration renewDeadline) {
    this.renewDeadline = renewDeadline;
    return this;
  }

  public LeaderElectionConfigurationBuilder withRetryPeriod(Duration retryPeriod) {
    this.retryPeriod = retryPeriod;
    return this;
  }

  public LeaderElectionConfigurationBuilder withIdentity(String identity) {
    this.identity = identity;
    return this;
  }

  public LeaderElectionConfiguration build() {
    return new LeaderElectionConfiguration(leaseName, leaseNamespace, leaseDuration, renewDeadline,
        retryPeriod, identity);
  }
}
