package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.Optional;

import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;

public class LeaderElectionConfiguration {

  public static final Duration LEASE_DURATION_DEFAULT_VALUE = Duration.ofSeconds(15);
  public static final Duration RENEW_DEADLINE_DEFAULT_VALUE = Duration.ofSeconds(10);
  public static final Duration RETRY_PERIOD_DEFAULT_VALUE = Duration.ofSeconds(2);

  private final String leaseName;
  private final String leaseNamespace;
  private final String identity;

  private final Duration leaseDuration;
  private final Duration renewDeadline;
  private final Duration retryPeriod;

  private final LeaderCallbacks leaderCallbacks;

  public LeaderElectionConfiguration(String leaseName, String leaseNamespace, String identity) {
    this(
        leaseName,
        leaseNamespace,
        LEASE_DURATION_DEFAULT_VALUE,
        RENEW_DEADLINE_DEFAULT_VALUE,
        RETRY_PERIOD_DEFAULT_VALUE, identity, null);
  }

  public LeaderElectionConfiguration(String leaseName, String leaseNamespace) {
    this(
        leaseName,
        leaseNamespace,
        LEASE_DURATION_DEFAULT_VALUE,
        RENEW_DEADLINE_DEFAULT_VALUE,
        RETRY_PERIOD_DEFAULT_VALUE, null, null);
  }

  public LeaderElectionConfiguration(String leaseName) {
    this(
        leaseName,
        null,
        LEASE_DURATION_DEFAULT_VALUE,
        RENEW_DEADLINE_DEFAULT_VALUE,
        RETRY_PERIOD_DEFAULT_VALUE, null, null);
  }

  public LeaderElectionConfiguration(
      String leaseName,
      String leaseNamespace,
      Duration leaseDuration,
      Duration renewDeadline,
      Duration retryPeriod) {
    this(leaseName, leaseNamespace, leaseDuration, renewDeadline, retryPeriod, null, null);
  }

  public LeaderElectionConfiguration(
      String leaseName,
      String leaseNamespace,
      Duration leaseDuration,
      Duration renewDeadline,
      Duration retryPeriod,
      String identity,
      LeaderCallbacks leaderCallbacks) {
    this.leaseName = leaseName;
    this.leaseNamespace = leaseNamespace;
    this.leaseDuration = leaseDuration;
    this.renewDeadline = renewDeadline;
    this.retryPeriod = retryPeriod;
    this.identity = identity;
    this.leaderCallbacks = leaderCallbacks;
  }

  public Optional<String> getLeaseNamespace() {
    return Optional.ofNullable(leaseNamespace);
  }

  public String getLeaseName() {
    return leaseName;
  }

  public Duration getLeaseDuration() {
    return leaseDuration;
  }

  public Duration getRenewDeadline() {
    return renewDeadline;
  }

  public Duration getRetryPeriod() {
    return retryPeriod;
  }

  public Optional<String> getIdentity() {
    return Optional.ofNullable(identity);
  }

  public Optional<LeaderCallbacks> getLeaderCallbacks() {
    return Optional.ofNullable(leaderCallbacks);
  }
}
