package io.javaoperatorsdk.operator;

import java.util.UUID;

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
  private ControllerManager controllerManager;

  public LeaderElectionManager(ControllerManager controllerManager) {
    this.controllerManager = controllerManager;
  }

  public void init(LeaderElectionConfiguration config, KubernetesClient client) {
    Lock lock = new LeaseLock(config.getLeaseNamespace(), config.getLeaseName(), identity(config));
    // todo releaseOnCancel
    // todo use this executor service?
    leaderElector = new LeaderElectorBuilder(client,
        ConfigurationServiceProvider.instance().getExecutorService())
        .withConfig(
            new LeaderElectionConfig(lock, config.getLeaseDuration(), config.getRenewDeadline(),
                config.getRetryPeriod(), leaderCallbacks(), true, config.getLeaseName()))
        .build();
  }


  public boolean isLeaderElectionOn() {
    return leaderElector != null;
  }

  private LeaderCallbacks leaderCallbacks() {
    return new LeaderCallbacks(this::startLeading, this::stopLeading, leader -> {
      log.info("New leader with identity: {}", leader);
    });
  }

  private void startLeading() {

  }

  private void stopLeading() {

  }

  private String identity(LeaderElectionConfiguration config) {
    String identity = config.getIdentity().orElse(System.getenv("HOSTNAME"));
    if (identity == null || identity.isBlank()) {
      identity = UUID.randomUUID().toString();
    }
    return identity;
  }

}
