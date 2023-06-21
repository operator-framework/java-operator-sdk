package io.javaoperatorsdk.operator;

import java.util.Arrays;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectRulesReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectRulesReviewSpecBuilder;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.authorization.v1.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

public class LeaderElectionManager {

  private static final Logger log = LoggerFactory.getLogger(LeaderElectionManager.class);

  public static final String NO_PERMISSION_TO_LEASE_RESOURCE_MESSAGE =
      "No permission to lease resource.";

  private LeaderElector leaderElector = null;
  private final ControllerManager controllerManager;
  private String identity;
  private CompletableFuture<?> leaderElectionFuture;
  private KubernetesClient client;
  private final ConfigurationService configurationService;
  private String leaseNamespace;

  public LeaderElectionManager(KubernetesClient kubernetesClient,
      ControllerManager controllerManager,
      ConfigurationService configurationService) {
    this.kubernetesClient = kubernetesClient;
    this.controllerManager = controllerManager;
    this.configurationService = configurationService;
  }

  public boolean isLeaderElectionEnabled() {
    return configurationService.getLeaderElectionConfiguration().isPresent();
  }

  public void init(LeaderElectionConfiguration config) {
    this.identity = identity(config);
    leaseNamespace =
            config.getLeaseNamespace().orElseGet(
                    () -> configurationService.getClientConfiguration().getNamespace());
    if (leaseNamespace == null) {
      final var message =
              "Lease namespace is not set and cannot be inferred. Leader election cannot continue.";
      log.error(message);
      throw new IllegalArgumentException(message);
    }
    final var lock = new LeaseLock(leaseNamespace, config.getLeaseName(), identity);
    // releaseOnCancel is not used in the underlying implementation
    leaderElector = new LeaderElectorBuilder(
            kubernetesClient, configurationService.getExecutorServiceManager().cachingExecutorService())
            .withConfig(
                    new LeaderElectionConfig(
                            lock,
                            config.getLeaseDuration(),
                            config.getRenewDeadline(),
                            config.getRetryPeriod(),
                            leaderCallbacks(),
                            true,
                            config.getLeaseName()))
            .build();
  }



  private LeaderCallbacks leaderCallbacks() {
    return new LeaderCallbacks(
        this::startLeading,
        this::stopLeading,
        leader -> log.info("New leader with identity: {}", leader));
  }

  private void startLeading() {
    controllerManager.startEventProcessing();
  }

  private void stopLeading() {
    log.info("Stopped leading for identity: {}. Exiting.", identity);
    // When leader stops leading the process ends immediately to prevent multiple reconciliations
    // running parallel.
    // Note that some reconciliations might run for a very long time.
    System.exit(1);
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
    if (leaderElectionFuture != null) {
      leaderElectionFuture.cancel(false);
    }
  }

  private void checkLeaseAccess() {
    var verbs = Arrays.asList("create", "update", "get");
    SelfSubjectRulesReview review = new SelfSubjectRulesReview();
    review.setSpec(new SelfSubjectRulesReviewSpecBuilder().withNamespace(leaseNamespace).build());
    var reviewResult = client.resource(review).create();
    log.debug("SelfSubjectRulesReview result: {}", reviewResult);
    var foundRule = reviewResult.getStatus().getResourceRules().stream()
            .filter(rule -> rule.getApiGroups().contains("coordination.k8s.io")
                    && rule.getResources().contains("leases")
                    && (rule.getVerbs().containsAll(verbs)) || rule.getVerbs().contains("*"))
            .findAny();
    if (foundRule.isEmpty()) {
      throw new OperatorException(NO_PERMISSION_TO_LEASE_RESOURCE_MESSAGE +
              " in namespace: " + leaseNamespace);
    }
  }
}
