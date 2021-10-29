package io.javaoperatorsdk.operator;

import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Version;
import io.javaoperatorsdk.operator.api.LifecycleAware;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.processing.ConfiguredController;

@SuppressWarnings("rawtypes")
public class Operator implements AutoCloseable, LifecycleAware {
  private static final Logger log = LoggerFactory.getLogger(Operator.class);
  private final KubernetesClient kubernetesClient;
  private final ConfigurationService configurationService;
  private final ControllerManager controllers = new ControllerManager();

  public Operator(ConfigurationService configurationService) {
    this(new DefaultKubernetesClient(), configurationService);
  }

  public Operator(KubernetesClient kubernetesClient, ConfigurationService configurationService) {
    this.kubernetesClient = kubernetesClient;
    this.configurationService = configurationService;
  }

  /** Adds a shutdown hook that automatically calls {@link #close()} when the app shuts down. */
  public void installShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::close));
  }

  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  public List<ConfiguredController> getControllers() {
    return new ArrayList<>(controllers.controllers.values());
  }

  /**
   * Finishes the operator startup process. This is mostly used in injection-aware applications
   * where there is no obvious entrypoint to the application which can trigger the injection process
   * and start the cluster monitoring processes.
   */
  public void start() {
    controllers.shouldStart();

    final var version = configurationService.getVersion();
    log.info(
        "Operator SDK {} (commit: {}) built on {} starting...",
        version.getSdkVersion(),
        version.getCommit(),
        version.getBuiltTime());

    log.info("Client version: {}", Version.clientVersion());
    try {
      final var k8sVersion = kubernetesClient.getVersion();
      if (k8sVersion != null) {
        log.info("Server version: {}.{}", k8sVersion.getMajor(), k8sVersion.getMinor());
      }
    } catch (Exception e) {
      final String error;
      if (e.getCause() instanceof ConnectException) {
        error = "Cannot connect to cluster";
      } else {
        error = "Error retrieving the server version";
      }
      log.error(error, e);
      throw new OperatorException(error, e);
    }

    ExecutorServiceManager.init(configurationService);
    controllers.start();
  }

  @Override
  public void stop() throws OperatorException {
    log.info(
        "Operator SDK {} is shutting down...", configurationService.getVersion().getSdkVersion());

    controllers.stop();

    ExecutorServiceManager.stop();
    kubernetesClient.close();
  }

  /** Stop the operator. */
  @Override
  public void close() {
    stop();
  }

  /**
   * Add a registration requests for the specified controller with this operator. The effective
   * registration of the controller is delayed till the operator is started.
   *
   * @param controller the controller to register
   * @param <R> the {@code CustomResource} type associated with the controller
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <R extends CustomResource<?, ?>> void register(ResourceController<R> controller)
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
  public <R extends CustomResource<?, ?>> void register(
      ResourceController<R> controller, ControllerConfiguration<R> configuration)
      throws OperatorException {
    final var existing = configurationService.getConfigurationFor(controller);
    if (existing == null) {
      throw new OperatorException(
          "Cannot register controller with name " + controller.getClass().getCanonicalName() +
              " controller named " + ControllerUtils.getNameFor(controller)
              + " because its configuration cannot be found.\n" +
              " Known controllers are: " + configurationService.getKnownControllerNames());
    } else {
      if (configuration == null) {
        configuration = existing;
      }
      final var configuredController =
          new ConfiguredController<>(controller, configuration, kubernetesClient);
      controllers.add(configuredController);

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

  private static class ControllerManager implements LifecycleAware {
    private final Map<String, ConfiguredController> controllers = new HashMap<>();
    private boolean started = false;


    public synchronized void shouldStart() {
      if (started) {
        return;
      }
      if (controllers.isEmpty()) {
        throw new OperatorException("No ResourceController exists. Exiting!");
      }
    }

    public synchronized void start() {
      controllers.values().parallelStream().forEach(ConfiguredController::start);
      started = true;
    }

    public synchronized void stop() {
      if (!started) {
        return;
      }

      this.controllers.values().parallelStream().forEach(closeable -> {
        log.debug("closing {}", closeable);
        closeable.stop();
      });

      started = false;
    }

    public synchronized void add(ConfiguredController configuredController) {
      final var configuration = configuredController.getConfiguration();
      final var crdName = configuration.getCRDName();
      final var existing = controllers.get(crdName);
      if (existing != null) {
        throw new OperatorException("Cannot register controller " + configuration.getName()
            + ": another controller (" + existing.getConfiguration().getName()
            + ") is already registered for CRD " + crdName);
      }
      this.controllers.put(crdName, configuredController);
      if (started) {
        configuredController.start();
      }
    }
  }
}
