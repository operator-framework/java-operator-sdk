package io.javaoperatorsdk.operator;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.authorization.v1.ResourceAttributesBuilder;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewSpecBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
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
  private String leaseName;
  private String leaseNamespace;

  public LeaderElectionManager(ControllerManager controllerManager) {
    this.controllerManager = controllerManager;
  }

  public void init(LeaderElectionConfiguration config, KubernetesClient client) {
    this.client = client;
    this.identity = identity(config);
    this.leaseName = config.getLeaseName();
    leaseNamespace =
        config.getLeaseNamespace().orElseGet(
            () -> ConfigurationServiceProvider.instance().getClientConfiguration().getNamespace());
    if (leaseNamespace == null) {
      final var message =
          "Lease namespace is not set and cannot be inferred. Leader election cannot continue.";
      log.error(message);
      throw new IllegalArgumentException(message);
    }
    final var lock = new LeaseLock(leaseNamespace, leaseName, identity);
    // releaseOnCancel is not used in the underlying implementation
    leaderElector =
        new LeaderElectorBuilder(
            client, ExecutorServiceManager.instance().executorService())
            .withConfig(new LeaderElectionConfig(
                lock,
                config.getLeaseDuration(),
                config.getRenewDeadline(),
                config.getRetryPeriod(),
                leaderCallbacks(),
                true,
                config.getLeaseName()))
            .build();
  }

  public boolean isLeaderElectionEnabled() {
    return leaderElector != null;
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
    var verbs = new String[] {"create", "update", "get"};
    for (String verb : verbs) {
      var allowed = checkLeaseAccess(verb);
      if (!allowed) {
        throw new OperatorException(NO_PERMISSION_TO_LEASE_RESOURCE_MESSAGE);
      }
    }
  }

  private boolean checkLeaseAccess(String verb) {
    var res = client.resource(selfSubjectReview(verb, leaseName, leaseNamespace)).create();
    return res.getStatus().getAllowed();
  }

  private SelfSubjectAccessReview selfSubjectReview(String verb, String name, String namespace) {
    var res = new SelfSubjectAccessReview();
    res.setSpec(new SelfSubjectAccessReviewSpecBuilder()
        .withResourceAttributes(new ResourceAttributesBuilder()
            .withGroup("coordination.k8s.io")
            .withNamespace(namespace)
            .withName(name)
            .withVerb(verb)
            .build())
        .build());
    return res;
  }
}
