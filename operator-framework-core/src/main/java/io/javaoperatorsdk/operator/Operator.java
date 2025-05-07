package io.javaoperatorsdk.operator;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Version;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider;
import io.javaoperatorsdk.operator.api.config.UpdatableConfigurationService;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;

@SuppressWarnings("rawtypes")
public class Operator implements LifecycleAware {
  private static final Logger log = LoggerFactory.getLogger(Operator.class);

  private final ControllerManager controllerManager;
  private final LeaderElectionManager leaderElectionManager;
  private final UpdatableConfigurationService configurationService;
  private volatile boolean started = false;

  public Operator() {
    this((KubernetesClient) null);
  }

  Operator(KubernetesClient kubernetesClient) {
    this(initConfigurationService(kubernetesClient, null));
  }

  /**
   * Creates an Operator based on the configuration provided by the specified {@link
   * ConfigurationService}. If you intend to use different values than the default, you use {@link
   * Operator#Operator(Consumer)} instead to override the default with your intended setup.
   *
   * @param configurationService a {@link ConfigurationService} providing the configuration for the
   *     operator
   */
  public Operator(UpdatableConfigurationService configurationService) {
    this.configurationService = configurationService;

    final var executorServiceManager = configurationService.getExecutorServiceManager();
    controllerManager = new ControllerManager(executorServiceManager);

    leaderElectionManager = new LeaderElectionManager(controllerManager, configurationService);
  }

  /**
   * Creates an Operator overriding the default configuration with the values provided by the
   * specified {@link ConfigurationServiceOverrider}.
   *
   * @param overrider a {@link ConfigurationServiceOverrider} consumer used to override the default
   *     {@link ConfigurationService} values
   */
  public Operator(Consumer<ConfigurationServiceOverrider> overrider) {
    this(initConfigurationService(null, overrider));
  }

  private static UpdatableConfigurationService initConfigurationService(
      KubernetesClient client, Consumer<ConfigurationServiceOverrider> overrider) {
    // initialize the client if the user didn't provide one
    if (client == null) {
      var configurationService = ConfigurationService.newOverriddenConfigurationService(overrider);
      client = configurationService.getKubernetesClient();
    }

    final var kubernetesClient = client;

    // override the configuration service to use the same client
    if (overrider != null) {
      overrider = overrider.andThen(o -> o.withKubernetesClient(kubernetesClient));
    } else {
      overrider = o -> o.withKubernetesClient(kubernetesClient);
    }

    return UpdatableConfigurationService.newOverriddenConfigurationService(overrider);
  }

  /**
   * Adds a shutdown hook that automatically calls {@link #stop()} when the app shuts down. Note
   * that graceful shutdown is usually not needed, but some {@link Reconciler} implementations might
   * require it.
   *
   * <p>Note that you might want to tune "terminationGracePeriodSeconds" for the Pod running the
   * controller.
   *
   * @param gracefulShutdownTimeout timeout to wait for executor threads to complete actual
   *     reconciliations
   */
  @SuppressWarnings("unused")
  public void installShutdownHook(Duration gracefulShutdownTimeout) {
    if (!leaderElectionManager.isLeaderElectionEnabled()) {
      Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    } else {
      log.warn("Leader election is on, shutdown hook will not be installed.");
    }
  }

  public KubernetesClient getKubernetesClient() {
    return configurationService.getKubernetesClient();
  }

  /**
   * Finishes the operator startup process. This is mostly used in injection-aware applications
   * where there is no obvious entrypoint to the application which can trigger the injection process
   * and start the cluster monitoring processes.
   */
  public synchronized void start() {
    try {
      if (started) {
        return;
      }
      controllerManager.shouldStart();
      final var version = configurationService.getVersion();
      log.info(
          "Operator SDK {} (commit: {}) built on {} starting...",
          version.getSdkVersion(),
          version.getCommit(),
          version.getBuiltTime());
      final var clientVersion = Version.clientVersion();
      log.info("Client version: {}", clientVersion);

      // need to create new thread pools if we're restarting because they've been shut down when we
      // previously stopped
      configurationService.getExecutorServiceManager().start(configurationService);

      // first start the controller manager before leader election,
      // the leader election would start subsequently the processor if on
      controllerManager.start(!leaderElectionManager.isLeaderElectionEnabled());
      leaderElectionManager.start();
      started = true;
    } catch (Exception e) {
      stop();
      throw new OperatorException("Error starting operator", e);
    }
  }

  @Override
  public void stop() throws OperatorException {
    Duration reconciliationTerminationTimeout =
        configurationService.reconciliationTerminationTimeout();
    if (!started) {
      return;
    }
    log.info(
        "Operator SDK {} is shutting down...", configurationService.getVersion().getSdkVersion());
    controllerManager.stop();

    configurationService.getExecutorServiceManager().stop(reconciliationTerminationTimeout);
    leaderElectionManager.stop();
    if (configurationService.closeClientOnStop()) {
      getKubernetesClient().close();
    }

    started = false;
  }

  /**
   * Add a registration requests for the specified reconciler with this operator. The effective
   * registration of the reconciler is delayed till the operator is started.
   *
   * @param reconciler the reconciler to register
   * @param <P> the {@code CustomResource} type associated with the reconciler
   * @return registered controller
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <P extends HasMetadata> RegisteredController<P> register(Reconciler<P> reconciler)
      throws OperatorException {
    final var controllerConfiguration = configurationService.getConfigurationFor(reconciler);
    return register(reconciler, controllerConfiguration);
  }

  /**
   * Add a registration requests for the specified reconciler with this operator, overriding its
   * default configuration by the specified one (usually created via {@link
   * io.javaoperatorsdk.operator.api.config.ControllerConfigurationOverrider#override(ControllerConfiguration)},
   * passing it the reconciler's original configuration. The effective registration of the
   * reconciler is delayed till the operator is started.
   *
   * @param reconciler part of the reconciler to register
   * @param configuration the configuration with which we want to register the reconciler
   * @param <P> the {@code HasMetadata} type associated with the reconciler
   * @return registered controller
   * @throws OperatorException if a problem occurred during the registration process
   */
  public <P extends HasMetadata> RegisteredController<P> register(
      Reconciler<P> reconciler, ControllerConfiguration<P> configuration) throws OperatorException {
    if (started) {
      throw new OperatorException("Operator already started. Register all the controllers before.");
    }

    if (configuration == null) {
      throw new OperatorException(
          "Cannot register reconciler with name "
              + reconciler.getClass().getCanonicalName()
              + " reconciler named "
              + ReconcilerUtils.getNameFor(reconciler)
              + " because its configuration cannot be found.\n"
              + " Known reconcilers are: "
              + configurationService.getKnownReconcilerNames());
    }

    // update the reconciler's configuration in the ConfigurationService if needed
    final var existing = configurationService.getConfigurationFor(reconciler);
    if (!configuration.equals(existing)) {
      configurationService.replace(configuration);
    }

    final var controller = new Controller<>(reconciler, configuration, getKubernetesClient());

    controllerManager.add(controller);

    final var informerConfig = configuration.getInformerConfig();
    final var watchedNS =
        informerConfig.watchAllNamespaces()
            ? "[all namespaces]"
            : informerConfig.getEffectiveNamespaces(configuration);

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
   * @return registered controller
   * @param <P> the {@code HasMetadata} type associated with the reconciler
   */
  public <P extends HasMetadata> RegisteredController<P> register(
      Reconciler<P> reconciler, Consumer<ControllerConfigurationOverrider<P>> configOverrider) {
    final var controllerConfiguration = configurationService.getConfigurationFor(reconciler);
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

  public RuntimeInfo getRuntimeInfo() {
    return new RuntimeInfo(this);
  }

  boolean isStarted() {
    return started;
  }

  public ConfigurationService getConfigurationService() {
    return configurationService;
  }
}
