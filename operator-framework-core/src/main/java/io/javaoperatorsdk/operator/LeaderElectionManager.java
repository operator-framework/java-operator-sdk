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
package io.javaoperatorsdk.operator;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.authorization.v1.ResourceRule;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectRulesReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectRulesReviewSpecBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

/**
 * Manages the leader-election lifecycle for an {@link Operator} instance. Leader election ensures
 * that, in a high-availability setup with multiple replicas of the same operator, only one replica
 * at a time actively reconciles resources. The replica currently holding the lease is referred to
 * as the leader, and the others stand by until the lease becomes available.
 *
 * <p>Leader election is opt-in. It is enabled when a {@link LeaderElectionConfiguration} is
 * supplied via {@link
 * io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider#withLeaderElectionConfiguration(LeaderElectionConfiguration)
 * ConfigurationServiceOverrider#withLeaderElectionConfiguration(LeaderElectionConfiguration)}. The
 * configuration controls the lease name, namespace, durations, and optional user-supplied {@link
 * LeaderCallbacks}.
 *
 * <p>Internally this class wraps a Fabric8 {@link LeaderElector} that coordinates via a Kubernetes
 * {@code Lease} resource (group {@value #COORDINATION_GROUP}, resource {@value #LEASES_RESOURCE}).
 * When this pod acquires the lease, {@link #startLeading()} starts event processing on the
 * controller manager. When the lease is lost or the leader-election future is cancelled, {@link
 * #stopLeading()} is invoked.
 *
 * <p>{@link #stopLeading()} behaves differently depending on how it was triggered:
 *
 * <ul>
 *   <li>If {@link #stop()} has already been called (graceful shutdown), it logs and returns without
 *       exiting. This avoids deadlocking against the JVM shutdown hook lock when {@link
 *       Operator#stop()} is invoked from a JVM shutdown hook.
 *   <li>Otherwise, if the configured {@link LeaderElectionConfiguration#isExitOnStopLeading()} is
 *       {@code true} (the default), it calls {@code System.exit(1)} so the process restarts and
 *       another replica can take over.
 *   <li>If {@code isExitOnStopLeading()} is {@code false}, it only logs and returns.
 * </ul>
 *
 * <p>The lifecycle methods {@link #start()} and {@link #stop()} are called by {@link Operator} as
 * part of {@link Operator#start()} and {@link Operator#stop()} respectively. Users typically do not
 * interact with this class directly.
 */
public class LeaderElectionManager {

  private static final Logger log = LoggerFactory.getLogger(LeaderElectionManager.class);

  public static final String NO_PERMISSION_TO_LEASE_RESOURCE_MESSAGE =
      "No permission to lease resource.";
  public static final String UNIVERSAL_VALUE = "*";
  public static final String COORDINATION_GROUP = "coordination.k8s.io";
  public static final String LEASES_RESOURCE = "leases";

  private LeaderElector leaderElector = null;
  private final ControllerManager controllerManager;
  private String identity;
  private CompletableFuture<?> leaderElectionFuture;
  private final ConfigurationService configurationService;
  private String leaseNamespace;
  private String leaseName;
  // Set in stop() before cancelling the leader-election future. Checked in stopLeading() so that
  // a graceful shutdown does not call System.exit, which would otherwise deadlock against the
  // JVM shutdown hook lock when stop() is invoked from a JVM shutdown hook.
  private final AtomicBoolean stoppingGracefully = new AtomicBoolean(false);

  LeaderElectionManager(
      ControllerManager controllerManager, ConfigurationService configurationService) {
    this.controllerManager = controllerManager;
    this.configurationService = configurationService;
  }

  public boolean isLeaderElectionEnabled() {
    return configurationService.getLeaderElectionConfiguration().isPresent();
  }

  private void init(LeaderElectionConfiguration config) {
    this.identity = identity(config);
    leaseNamespace =
        config
            .getLeaseNamespace()
            .orElseGet(
                () -> configurationService.getKubernetesClient().getConfiguration().getNamespace());
    if (leaseNamespace == null) {
      final var message =
          "Lease namespace is not set and cannot be inferred. Leader election cannot continue.";
      log.error(message);
      throw new IllegalArgumentException(message);
    }
    leaseName = config.getLeaseName();
    final var lock = new LeaseLock(leaseNamespace, leaseName, identity);
    leaderElector =
        new LeaderElectorBuilder(
                configurationService.getKubernetesClient(),
                configurationService.getExecutorServiceManager().cachingExecutorService())
            .withConfig(
                new LeaderElectionConfig(
                    lock,
                    config.getLeaseDuration(),
                    config.getRenewDeadline(),
                    config.getRetryPeriod(),
                    leaderCallbacks(config),
                    // this is required to be false to receive stop event in all cases, thus
                    // stopLeading
                    // is called always when leadership is lost/cancelled
                    false,
                    leaseName))
            .build();
  }

  private LeaderCallbacks leaderCallbacks(LeaderElectionConfiguration config) {
    return new LeaderCallbacks(
        () -> {
          config.getLeaderCallbacks().ifPresent(LeaderCallbacks::onStartLeading);
          LeaderElectionManager.this.startLeading();
        },
        () -> {
          config.getLeaderCallbacks().ifPresent(LeaderCallbacks::onStopLeading);
          LeaderElectionManager.this.stopLeading();
        },
        leader -> {
          config.getLeaderCallbacks().ifPresent(cb -> cb.onNewLeader(leader));
          log.info("New leader with identity: {}", leader);
        });
  }

  private void startLeading() {
    controllerManager.startEventProcessing();
  }

  protected void stopLeading() {
    if (stoppingGracefully.get()) {
      log.info("Stopped leading for identity: {} during graceful shutdown.", identity);
      return;
    }
    if (configurationService.getLeaderElectionConfiguration().orElseThrow().isExitOnStopLeading()) {
      log.info("Stopped leading for identity: {}. Exiting.", identity);
      // When leader stops leading the process ends immediately to prevent multiple reconciliations
      // running parallel.
      // Note that some reconciliations might run for a very long time.
      System.exit(1);
    } else {
      log.info("Stopped leading, configured not to exit");
    }
  }

  private String identity(LeaderElectionConfiguration config) {
    var id = config.getIdentity().orElseGet(() -> System.getenv("HOSTNAME"));
    if (id == null || id.isBlank()) {
      id = UUID.randomUUID().toString();
    }
    return id;
  }

  public void start() {
    if (isLeaderElectionEnabled()) {
      init(configurationService.getLeaderElectionConfiguration().orElseThrow());
      checkLeaseAccess();
      leaderElectionFuture = leaderElector.start();
    }
  }

  public void stop() {
    stoppingGracefully.set(true);
    if (leaderElectionFuture != null) {
      leaderElectionFuture.cancel(false);
    }
  }

  private void checkLeaseAccess() {
    var verbsRequired = Arrays.asList("create", "update", "get");
    SelfSubjectRulesReview review = new SelfSubjectRulesReview();
    review.setSpec(new SelfSubjectRulesReviewSpecBuilder().withNamespace(leaseNamespace).build());
    var reviewResult = configurationService.getKubernetesClient().resource(review).create();
    log.debug("SelfSubjectRulesReview result: {}", reviewResult);
    var verbsAllowed =
        reviewResult.getStatus().getResourceRules().stream()
            .filter(rule -> matchesValue(rule.getApiGroups(), COORDINATION_GROUP))
            .filter(rule -> matchesValue(rule.getResources(), LEASES_RESOURCE))
            .filter(
                rule ->
                    rule.getResourceNames().isEmpty()
                        || rule.getResourceNames().contains(leaseName))
            .map(ResourceRule::getVerbs)
            .flatMap(Collection::stream)
            .distinct()
            .collect(Collectors.toList());
    if (verbsAllowed.contains(UNIVERSAL_VALUE)
        || new HashSet<>(verbsAllowed).containsAll(verbsRequired)) {
      return;
    }

    var missingVerbs =
        verbsRequired.stream()
            .filter(Predicate.not(verbsAllowed::contains))
            .collect(Collectors.toList());

    throw new OperatorException(
        NO_PERMISSION_TO_LEASE_RESOURCE_MESSAGE
            + " in namespace: "
            + leaseNamespace
            + "; missing required verbs: "
            + missingVerbs);
  }

  private boolean matchesValue(Collection<String> values, String match) {
    return values.contains(match) || values.contains(UNIVERSAL_VALUE);
  }
}
