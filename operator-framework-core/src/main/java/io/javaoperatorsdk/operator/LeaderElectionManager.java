package io.javaoperatorsdk.operator;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.Lock;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

public class LeaderElectionManager {

  private static final Logger log = LoggerFactory.getLogger(LeaderElectionManager.class);

  private LeaderElector leaderElector = null;
  private final ControllerManager controllerManager;
  private String identity;
  private CompletableFuture<?> leaderElectionFuture;

  public LeaderElectionManager(ControllerManager controllerManager) {
    this.controllerManager = controllerManager;
  }

  public void init(LeaderElectionConfiguration config, KubernetesClient client) {
    this.identity = identity(config);
    Lock lock = new LeaseLock(config.getLeaseNamespace(), config.getLeaseName(), identity);
    // releaseOnCancel is not used in the underlying implementation
    leaderElector = new LeaderElectorBuilder(client,
        ConfigurationServiceProvider.instance().getExecutorService())
        .withConfig(
            new LeaderElectionConfig(lock, config.getLeaseDuration(), config.getRenewDeadline(),
                config.getRetryPeriod(), leaderCallbacks(), true, config.getLeaseName()))
        .build();
  }

  public boolean isLeaderElectionEnabled() {
    return leaderElector != null;
  }

  private LeaderCallbacks leaderCallbacks() {
    return new LeaderCallbacks(this::startLeading, this::stopLeading, leader -> {
      log.info("New leader with identity: {}", leader);
    });
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
    String id = config.getIdentity().orElse(System.getenv("HOSTNAME"));
    if (id == null || id.isBlank()) {
      id = UUID.randomUUID().toString();
    }
    return id;
  }

  public void start() {
    if (isLeaderElectionEnabled()) {
      leaderElectionFuture = leaderElector.start();
    }
  }

  public void stop() {
    if (leaderElectionFuture != null) {
      leaderElectionFuture.cancel(false);
    }
  }
}
