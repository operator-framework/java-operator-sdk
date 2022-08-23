package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;
import java.util.UUID;

import io.fabric8.zjsonpatch.internal.guava.Strings;

public class LeaderElectionConfiguration {

  public static final Duration LEASE_DURATION_DEFAULT_VALUE = Duration.ofSeconds(15);
  public static final Duration RENEW_DEADLINE_DEFAULT_VALUE = Duration.ofSeconds(10);
  public static final Duration RETRY_PERIOD_DEFAULT_VALUE = Duration.ofSeconds(2);

  private final String leaseName;
  private final String leaseNamespace;
  private String identity;

  private final Duration leaseDuration;
  private final Duration renewDeadline;
  private final Duration retryPeriod;

  public LeaderElectionConfiguration(String leaseName, String leaseNamespace) {
    this(
        leaseName,
        leaseNamespace,
        LEASE_DURATION_DEFAULT_VALUE,
        RENEW_DEADLINE_DEFAULT_VALUE,
        RETRY_PERIOD_DEFAULT_VALUE, null);
  }

  public LeaderElectionConfiguration(
      String leaseName,
      String leaseNamespace,
      Duration leaseDuration,
      Duration renewDeadline,
      Duration retryPeriod) {
    this(leaseName, leaseNamespace, leaseDuration, renewDeadline, retryPeriod, null);
  }

  public LeaderElectionConfiguration(
      String leaseName,
      String leaseNamespace,
      Duration leaseDuration,
      Duration renewDeadline,
      Duration retryPeriod,
      String identity) {
    this.leaseName = leaseName;
    this.leaseNamespace = leaseNamespace;
    this.leaseDuration = leaseDuration;
    this.renewDeadline = renewDeadline;
    this.retryPeriod = retryPeriod;
    this.identity = identity;
  }

  public String getLeaseNamespace() {
    return leaseNamespace;
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

  public String getIdentity() {
    if (identity == null) {
      identity = System.getenv("HOSTNAME");
      if (Strings.isNullOrEmpty(identity)) {
        identity = UUID.randomUUID().toString();
      } else {
        identity = identity.trim();
        if (identity.isBlank()) {
          identity = UUID.randomUUID().toString();
        }
      }
    }
    return identity;
  }
}
