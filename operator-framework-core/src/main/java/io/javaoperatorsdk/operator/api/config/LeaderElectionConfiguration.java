package io.javaoperatorsdk.operator.api.config;

import java.time.Duration;

// todo discuss leader election with lease vs for life, other options:
// see: https://pkg.go.dev/sigs.k8s.io/controller-runtime/pkg/manager#Options
public class LeaderElectionConfiguration {

  public static final Duration LEASE_DURATION_DEFAULT_VALUE = Duration.ofSeconds(15);
  public static final Duration RENEW_DEADLINE_DEFAULT_VALUE = Duration.ofSeconds(10);
  public static final Duration RETRY_PERIOD_DEFAULT_VALUE = Duration.ofSeconds(2);

  // todo discuss
  // private boolean syncEventSources;

  private final String leaseName;
  private final String leaseNamespace;

  private final Duration leaseDuration;
  private final Duration renewDeadline;
  private final Duration retryPeriod;

  public LeaderElectionConfiguration(String leaseName, String leaseNamespace) {
    this(
        leaseName,
        leaseNamespace,
        LEASE_DURATION_DEFAULT_VALUE,
        RENEW_DEADLINE_DEFAULT_VALUE,
        RETRY_PERIOD_DEFAULT_VALUE);
  }

  public LeaderElectionConfiguration(
      String leaseName,
      String leaseNamespace,
      Duration leaseDuration,
      Duration renewDeadline,
      Duration retryPeriod) {
    this.leaseName = leaseName;
    this.leaseNamespace = leaseNamespace;
    this.leaseDuration = leaseDuration;
    this.renewDeadline = renewDeadline;
    this.retryPeriod = retryPeriod;
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
}
