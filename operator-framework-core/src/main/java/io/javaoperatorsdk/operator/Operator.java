package io.javaoperatorsdk.operator;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.Version;
import io.javaoperatorsdk.operator.api.config.*;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;

@SuppressWarnings("rawtypes")
public class Operator implements LifecycleAware {
  private static final Logger log = LoggerFactory.getLogger(Operator.class);
  private final KubernetesClient kubernetesClient;
  private final ControllerManager controllerManager = new ControllerManager();
  private final LeaderElectionManager leaderElectionManager =
      new LeaderElectionManager(controllerManager);
  private volatile boolean started = false;

  public Operator() {
    this((KubernetesClient) null);
  }

  public Operator(KubernetesClient kubernetesClient) {
    this(kubernetesClient, ConfigurationServiceProvider.instance());
  }

  /**
   * @deprecated Use {@link #Operator(Consumer)} instead
   */
  @Deprecated(forRemoval = true)
  public Operator(ConfigurationService configurationService) {
    this(null, configurationService);
  }

  public Operator(Consumer<ConfigurationServiceOverrider> overrider) {
    this(null, overrider);
  }

  public Operator(KubernetesClient client, Consumer<ConfigurationServiceOverrider> overrider) {
    this(client, ConfigurationServiceProvider.overrideCurrent(overrider));
  }

  /**
   * Note that Operator by default closes the client on stop, this can be changed using
   * {@link ConfigurationService}
   *
   * @param kubernetesClient client to use to all Kubernetes related operations
   * @param configurationService provides configuration
   */
  public Operator(KubernetesClient kubernetesClient, ConfigurationService configurationService) {
    this.kubernetesClient =
        kubernetesClient != null ? kubernetesClient : new KubernetesClientBuilder().build();
    configurationService.getLeaderElectionConfiguration()
        .ifPresent(c -> leaderElectionManager.init(c, this.kubernetesClient));
    ConfigurationServiceProvider.set(configurationService);
  }

  /** Adds a shutdown hook that automatically calls {@link #stop()} when the app shuts down. */
  @Deprecated(forRemoval = true)
  public void installShutdownHook() {
    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
  }

  public KubernetesClient getKubernetesClient() {
    return kubernetesClient;
  }

  /**
   * Finishes the operator startup process. This is mostly used in injection-aware applications
   * where there is no obvious entrypoint to the application which can trigger the injection process
   * and start the cluster monitoring processes.
   */
  public void start() {
    try {
      if (started) {
        return;
      }
      started = true;
      controllerManager.shouldStart();
      final var version = ConfigurationServiceProvider.instance().getVersion();
      log.info(
          "Operator SDK {} (commit: {}) built on {} starting...",
          version.getSdkVersion(),
          version.getCommit(),
          version.getBuiltTime());

      final var clientVersion = Version.clientVersion();
      log.info("Client version: {}", clientVersion);
      ExecutorServiceManager.init();
      // first start the controller manager before leader election,
      // the leader election would start subsequently the processor if on
      controllerManager.start(!leaderElectionManager.isLeaderElectionEnabled());
      leaderElectionManager.start();
    } catch (Exception e) {
      log.error("Error starting operator", e);
      stop();
      throw e;
    }
  }

  @Override
  public void stop() throws OperatorException {
    final var configurationService = ConfigurationServiceProvider.instance();
    log.info(
        "Operator SDK {} is shutting down...", configurationService.getVersion().getSdkVersion());

    controllerManager.stop();
    ExecutorServiceManager.stop();
    leaderElectionManager.stop();
    if (configurationService.closeClientOnStop()) {
      kubernetesClient.close();
    }
  }

  /**
   * Add a registration requests for the specified reconciler with this operator. The effective
   * registration of the reconciler is delayed till the operator is started.
   *
   * @param reconciler the reconciler to register
   * @param <P> the {@code CustomResource} type associated with the reconciler
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <P extends HasMetadata> RegisteredController<P> register(Reconciler<P> reconciler)
      throws OperatorException {
    final var controllerConfiguration =
        ConfigurationServiceProvider.instance().getConfigurationFor(reconciler);
    return register(reconciler, controllerConfiguration);
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
   * @param <P> the {@code HasMetadata} type associated with the reconciler
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <P extends HasMetadata> RegisteredController<P> register(Reconciler<P> reconciler,
      ControllerConfiguration<P> configuration)
      throws OperatorException {
    if (started) {
      throw new OperatorException("Operator already started. Register all the controllers before.");
    }

    if (configuration == null) {
      throw new OperatorException(
          "Cannot register reconciler with name " + reconciler.getClass().getCanonicalName() +
              " reconciler named " + ReconcilerUtils.getNameFor(reconciler)
              + " because its configuration cannot be found.\n" +
              " Known reconcilers are: "
              + ConfigurationServiceProvider.instance().getKnownReconcilerNames());
    }

    final var controller = new Controller<>(reconciler, configuration, kubernetesClient);

    controllerManager.add(controller);

    final var watchedNS = configuration.watchAllNamespaces() ? "[all namespaces]"
        : configuration.getEffectiveNamespaces();

    log.info(
        "Registered reconciler: '{}' for resource: '{}' for namespace(s): {}",
        configuration.getName(),
        configuration.getResourceClass(),
        watchedNS);
    return controller;
  }

  /**
   * Method to register operator and facilitate configuration override.
   *
   * @param reconciler part of the reconciler to register
   * @param configOverrider consumer to use to change config values
   * @param <P> the {@code HasMetadata} type associated with the reconciler
   */
  public <P extends HasMetadata> RegisteredController<P> register(Reconciler<P> reconciler,
      Consumer<ControllerConfigurationOverrider<P>> configOverrider) {
    final var controllerConfiguration =
        ConfigurationServiceProvider.instance().getConfigurationFor(reconciler);
    var configToOverride = ControllerConfigurationOverrider.override(controllerConfiguration);
    configOverrider.accept(configToOverride);
    return register(reconciler, configToOverride.build());
  }

  public Optional<RegisteredController> getRegisteredController(String name) {
    return controllerManager.get(name).map(RegisteredController.class::cast);
  }

  public Set<RegisteredController> getRegisteredControllers() {
    return new HashSet<>(controllerManager.controllers());
  }

  public int getRegisteredControllersNumber() {
    return controllerManager.size();
  }

}
