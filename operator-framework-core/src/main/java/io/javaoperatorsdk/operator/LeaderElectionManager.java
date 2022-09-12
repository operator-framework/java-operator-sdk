package io.javaoperatorsdk.operator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
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
import io.fabric8.kubernetes.client.utils.Utils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;

import static io.fabric8.kubernetes.client.Config.KUBERNETES_NAMESPACE_FILE;
import static io.fabric8.kubernetes.client.Config.KUBERNETES_NAMESPACE_PATH;

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
    final var leaseNamespace =
        config.getLeaseNamespace().or(LeaderElectionManager::tryNamespaceFromPath);
    if (leaseNamespace.isEmpty()) {
      final var message =
          "Lease namespace is not set and cannot be inferred. Leader election cannot continue.";
      log.error(message);
      throw new IllegalArgumentException(message);
    }
    final var lock = new LeaseLock(leaseNamespace.orElseThrow(), config.getLeaseName(), identity);
    // releaseOnCancel is not used in the underlying implementation
    leaderElector =
        new LeaderElectorBuilder(
            client, ConfigurationServiceProvider.instance().getExecutorService())
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

  public boolean isLeaderElectionEnabled() {
    return leaderElector != null;
  }

  private LeaderCallbacks leaderCallbacks() {
    return new LeaderCallbacks(
        this::startLeading,
        this::stopLeading,
        leader -> {
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
    var id = config.getIdentity().orElse(System.getenv("HOSTNAME"));
    if (id == null || id.isBlank()) {
      id = UUID.randomUUID().toString();
    }
    return id;
  }

  private static Optional<String> tryNamespaceFromPath() {
    log.info("Trying to get namespace from Kubernetes service account namespace path...");

    final var serviceAccountNamespace =
        Utils.getSystemPropertyOrEnvVar(KUBERNETES_NAMESPACE_FILE, KUBERNETES_NAMESPACE_PATH);
    final var serviceAccountNamespacePath = Path.of(serviceAccountNamespace);

    final var serviceAccountNamespaceExists = Files.isRegularFile(serviceAccountNamespacePath);
    if (serviceAccountNamespaceExists) {
      log.info("Found service account namespace at: [{}].", serviceAccountNamespace);
      try {
        return Optional
            .of(Files.readString(serviceAccountNamespacePath).strip());
      } catch (IOException e) {
        log.error(
            "Error reading service account namespace from: [" + serviceAccountNamespace + "].", e);
        return Optional.empty();
      }
    }

    log.warn("Did not find service account namespace at: [{}].", serviceAccountNamespace);
    return Optional.empty();
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
