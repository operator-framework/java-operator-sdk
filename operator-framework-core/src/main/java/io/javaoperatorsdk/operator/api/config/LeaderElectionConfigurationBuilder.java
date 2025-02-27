package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;

import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;

import static io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration.*;

@SuppressWarnings("unused")
public final class LeaderElectionConfigurationBuilder {

  private final String leaseName;
  private String leaseNamespace;
  private String identity;
  private Duration leaseDuration = LEASE_DURATION_DEFAULT_VALUE;
  private Duration renewDeadline = RENEW_DEADLINE_DEFAULT_VALUE;
  private Duration retryPeriod = RETRY_PERIOD_DEFAULT_VALUE;
  private LeaderCallbacks leaderCallbacks;
  private boolean exitOnStopLeading = true;

  private LeaderElectionConfigurationBuilder(String leaseName) {
    this.leaseName = leaseName;
  }

  public static LeaderElectionConfigurationBuilder aLeaderElectionConfiguration(String leaseName) {
    return new LeaderElectionConfigurationBuilder(leaseName);
  }

  public LeaderElectionConfigurationBuilder withLeaseNamespace(String leaseNamespace) {
    this.leaseNamespace = leaseNamespace;
    return this;
  }

  public LeaderElectionConfigurationBuilder withIdentity(String identity) {
    this.identity = identity;
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

  public LeaderElectionConfigurationBuilder withLeaderCallbacks(LeaderCallbacks leaderCallbacks) {
    this.leaderCallbacks = leaderCallbacks;
    return this;
  }

  public LeaderElectionConfigurationBuilder withExitOnStopLeading(boolean exitOnStopLeading) {
    this.exitOnStopLeading = exitOnStopLeading;
    return this;
  }

  public LeaderElectionConfiguration build() {
    return new LeaderElectionConfiguration(
        leaseName,
        leaseNamespace,
        leaseDuration,
        renewDeadline,
        retryPeriod,
        identity,
        leaderCallbacks,
        exitOnStopLeading);
  }
}
