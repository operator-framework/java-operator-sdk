package io.javaoperatorsdk.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Version;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ExecutorServiceManager;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;

@SuppressWarnings("rawtypes")
public class Operator implements LifecycleAware {
  private static final Logger log = LoggerFactory.getLogger(Operator.class);
  private final KubernetesClient kubernetesClient;
  private final ConfigurationService configurationService;
  private final ControllerManager controllers = new ControllerManager();


  public Operator(ConfigurationService configurationService) {
    this(new DefaultKubernetesClient(), configurationService);
  }

  /**
   * Note that Operator by default closes the client on stop, this can be changed using
   * {@link ConfigurationService}
   *
   * @param kubernetesClient client to use to all Kubernetes related operations
   * @param configurationService provides configuration
   */
  public Operator(KubernetesClient kubernetesClient, ConfigurationService configurationService) {
    this.kubernetesClient = kubernetesClient;
    this.configurationService = configurationService;
  }

  /** Adds a shutdown hook that automatically calls {@link #stop()} ()} when the app shuts down. */
  public void installShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
  }

  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }

  public List<Controller> getControllers() {
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

    final var clientVersion = Version.clientVersion();
    log.info("Client version: {}", clientVersion);

    ExecutorServiceManager.init(configurationService);
    controllers.start();
  }

  @Override
  public void stop() throws OperatorException {
    log.info(
        "Operator SDK {} is shutting down...", configurationService.getVersion().getSdkVersion());

    controllers.stop();

    ExecutorServiceManager.stop();
    if (configurationService.closeClientOnStop()) {
      kubernetesClient.close();
    }
  }

  /**
   * Add a registration requests for the specified reconciler with this operator. The effective
   * registration of the reconciler is delayed till the operator is started.
   *
   * @param reconciler the reconciler to register
   * @param <R> the {@code CustomResource} type associated with the reconciler
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <R extends HasMetadata> void register(Reconciler<R> reconciler)
      throws OperatorException {
    final var controllerConfiguration = configurationService.getConfigurationFor(reconciler);
    register(reconciler, controllerConfiguration);
  }

  /**
   * Add a registration requests for the specified reconciler with this operator, overriding its
   * default configuration by the specified one (usually created via
   * {@link io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider#override(ControllerConfiguration)},
   * passing it the reconciler's original configuration. The effective registration of the
   * reconciler is delayed till the operator is started.
   *
   * @param reconciler part of the reconciler to register
   * @param configuration the configuration with which we want to register the reconciler
   * @param <R> the {@code CustomResource} type associated with the reconciler
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <R extends HasMetadata> void register(Reconciler<R> reconciler,
      ControllerConfiguration<R> configuration)
      throws OperatorException {

    if (configuration == null) {
      throw new OperatorException(
          "Cannot register reconciler with name " + reconciler.getClass().getCanonicalName() +
              " reconciler named " + ReconcilerUtils.getNameFor(reconciler)
              + " because its configuration cannot be found.\n" +
              " Known reconcilers are: " + configurationService.getKnownReconcilerNames());
    }

    final var controller = new Controller<>(reconciler, configuration, kubernetesClient);

    controllers.add(controller);

    final var watchedNS = configuration.watchAllNamespaces() ? "[all namespaces]"
        : configuration.getEffectiveNamespaces();

    log.info(
        "Registered reconciler: '{}' for resource: '{}' for namespace(s): {}",
        configuration.getName(),
        configuration.getResourceClass(),
        watchedNS);
  }

  static class ControllerManager implements LifecycleAware {
    private final Map<String, Controller> controllers = new HashMap<>();
    private boolean started = false;

    public synchronized void shouldStart() {
      if (started) {
        return;
      }
      if (controllers.isEmpty()) {
        throw new OperatorException("No Controller exists. Exiting!");
      }
    }

    public synchronized void start() {
      controllers.values().parallelStream().forEach(Controller::start);
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

    public synchronized void add(Controller controller) {
      final var configuration = controller.getConfiguration();
      final var resourceTypeName = configuration.getResourceTypeName();
      // final var existing = controllers.get(resourceTypeName);
      // if (existing != null) {
      // throw new OperatorException("Cannot register controller '" + configuration.getName()
      // + "': another controller named '" + existing.getConfiguration().getName()
      // + "' is already registered for resource '" + resourceTypeName + "'");
      // }
      this.controllers.put(resourceTypeName, controller);
      if (started) {
        controller.start();
      }
    }
  }
}
