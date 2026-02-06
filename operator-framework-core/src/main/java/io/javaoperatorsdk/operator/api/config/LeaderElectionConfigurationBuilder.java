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

  /** Set to false only for testing purposes. */
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
