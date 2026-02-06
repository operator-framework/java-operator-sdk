/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  private final boolean exitOnStopLeading;

  /**
   * @deprecated Use {@link LeaderElectionConfigurationBuilder} instead
   */
  @Deprecated(forRemoval = true)
  public LeaderElectionConfiguration(String leaseName, String leaseNamespace, String identity) {
    this(
        leaseName,
        leaseNamespace,
        LEASE_DURATION_DEFAULT_VALUE,
        RENEW_DEADLINE_DEFAULT_VALUE,
        RETRY_PERIOD_DEFAULT_VALUE,
        identity,
        null,
        true);
  }

  /**
   * @deprecated Use {@link LeaderElectionConfigurationBuilder} instead
   */
  @Deprecated(forRemoval = true)
  public LeaderElectionConfiguration(String leaseName, String leaseNamespace) {
    this(leaseName, leaseNamespace, null);
  }

  /**
   * @deprecated Use {@link LeaderElectionConfigurationBuilder} instead
   */
  @Deprecated(forRemoval = true)
  public LeaderElectionConfiguration(String leaseName) {
    this(leaseName, null);
  }

  /**
   * @deprecated Use {@link LeaderElectionConfigurationBuilder} instead
   */
  @Deprecated(forRemoval = true)
  public LeaderElectionConfiguration(
      String leaseName,
      String leaseNamespace,
      Duration leaseDuration,
      Duration renewDeadline,
      Duration retryPeriod) {
    this(leaseName, leaseNamespace, leaseDuration, renewDeadline, retryPeriod, null, null, true);
  }

  /**
   * @deprecated Use {@link LeaderElectionConfigurationBuilder} instead
   */
  @Deprecated // this will be made package-only
  public LeaderElectionConfiguration(
      String leaseName,
      String leaseNamespace,
      Duration leaseDuration,
      Duration renewDeadline,
      Duration retryPeriod,
      String identity,
      LeaderCallbacks leaderCallbacks,
      boolean exitOnStopLeading) {
    this.leaseName = leaseName;
    this.leaseNamespace = leaseNamespace;
    this.leaseDuration = leaseDuration;
    this.renewDeadline = renewDeadline;
    this.retryPeriod = retryPeriod;
    this.identity = identity;
    this.leaderCallbacks = leaderCallbacks;
    this.exitOnStopLeading = exitOnStopLeading;
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

  public boolean isExitOnStopLeading() {
    return exitOnStopLeading;
  }
}
