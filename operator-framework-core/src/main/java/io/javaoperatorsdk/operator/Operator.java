package io.javaoperatorsdk.operator;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Version;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.processing.ConfiguredController;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler;
import io.javaoperatorsdk.operator.processing.DefaultEventHandler.EventMonitor;
import io.javaoperatorsdk.operator.processing.event.Event;

@SuppressWarnings("rawtypes")
public class Operator implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(Operator.class);
  private final KubernetesClient k8sClient;
  private final ConfigurationService configurationService;
  private final Object lock;
  private final List<ConfiguredController> controllers;
  private volatile boolean started;

  public Operator(KubernetesClient k8sClient, ConfigurationService configurationService) {
    this.k8sClient = k8sClient;
    this.configurationService = configurationService;
    this.lock = new Object();
    this.controllers = new ArrayList<>();
    this.started = false;
    DefaultEventHandler.setEventMonitor(new EventMonitor() {
      @Override
      public void processedEvent(String uid, Event event) {
        configurationService.getMetrics().timeControllerEvents();
      }

      @Override
      public void failedEvent(String uid, Event event) {
        configurationService.getMetrics().timeControllerRetry();
      }
    });
  }

  /** Adds a shutdown hook that automatically calls {@link #close()} when the app shuts down. */
  public void installShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  public KubernetesClient getKubernetesClient() {
    return k8sClient;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  /**
   * Finishes the operator startup process. This is mostly used in injection-aware applications
   * where there is no obvious entrypoint to the application which can trigger the injection process
   * and start the cluster monitoring processes.
   */
  @SuppressWarnings("unchecked")
  public void start() {
    synchronized (lock) {
      if (started) {
        return;
      }

      final var version = configurationService.getVersion();
      log.info(
          "Operator SDK {} (commit: {}) built on {} starting...",
          version.getSdkVersion(),
          version.getCommit(),
          version.getBuiltTime());

      if (controllers.isEmpty()) {
        throw new OperatorException("No ResourceController exists. Exiting!");
      }

      log.info("Client version: {}", Version.clientVersion());
      try {
        final var k8sVersion = k8sClient.getVersion();
        if (k8sVersion != null) {
          log.info("Server version: {}.{}", k8sVersion.getMajor(), k8sVersion.getMinor());
        }
      } catch (Exception e) {
        log.error("Error retrieving the server version. Exiting!", e);
        throw new OperatorException("Error retrieving the server version", e);
      }

      controllers.forEach(ConfiguredController::start);

      started = true;
    }
  }

  /** Stop the operator. */
  @Override
  public void close() {
    synchronized (lock) {
      if (!started) {
        return;
      }

      log.info(
          "Operator SDK {} is shutting down...", configurationService.getVersion().getSdkVersion());

      for (Closeable closeable : this.controllers) {
        try {
          log.debug("closing {}", closeable);
          closeable.close();
        } catch (IOException e) {
          log.warn("Error closing {}", closeable, e);
        }
      }

      started = false;
    }
  }

  /**
   * Add a registration requests for the specified controller with this operator. The effective
   * registration of the controller is delayed till the operator is started.
   *
   * @param controller the controller to register
   * @param <R> the {@code CustomResource} type associated with the controller
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <R extends CustomResource> void register(ResourceController<R> controller)
      throws OperatorException {
    register(controller, null);
  }

  /**
   * Add a registration requests for the specified controller with this operator, overriding its
   * default configuration by the specified one (usually created via
   * {@link io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider#override(ControllerConfiguration)},
   * passing it the controller's original configuration. The effective registration of the
   * controller is delayed till the operator is started.
   *
   * @param controller the controller to register
   * @param configuration the configuration with which we want to register the controller, if {@code
   *     null}, the controller's original configuration is used
   * @param <R> the {@code CustomResource} type associated with the controller
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <R extends CustomResource> void register(
      ResourceController<R> controller, ControllerConfiguration<R> configuration)
      throws OperatorException {
    final var existing = configurationService.getConfigurationFor(controller);
    if (existing == null) {
      log.warn(
          "Skipping registration of {} controller named {} because its configuration cannot be found.\n"
              + "Known controllers are: {}",
          controller.getClass().getCanonicalName(),
          ControllerUtils.getNameFor(controller),
          configurationService.getKnownControllerNames());
    } else {
      if (configuration == null) {
        configuration = existing;
      }
      synchronized (lock) {
        final var configuredController =
            new ConfiguredController(controller, configuration, k8sClient);
        this.controllers.add(configuredController);
        if (started) {
          configuredController.start();
        }
      }

      final var watchedNS =
          configuration.watchAllNamespaces()
              ? "[all namespaces]"
              : configuration.getEffectiveNamespaces();
      log.info(
          "Registered Controller: '{}' for CRD: '{}' for namespace(s): {}",
          configuration.getName(),
          configuration.getCustomResourceClass(),
          watchedNS);
    }
  }
}
