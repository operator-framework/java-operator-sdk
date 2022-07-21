package io.javaoperatorsdk.operator;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.Version;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.Lock;
import io.javaoperatorsdk.operator.api.config.*;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.processing.Controller;
import io.javaoperatorsdk.operator.processing.LifecycleAware;

@SuppressWarnings("rawtypes")
public class Operator implements LifecycleAware {
  private static final Logger log = LoggerFactory.getLogger(Operator.class);
  private final KubernetesClient kubernetesClient;
  private final ControllerManager controllers = new ControllerManager();
  private LeaderElector leaderElector;

  public Operator() {
    this(new DefaultKubernetesClient(), ConfigurationServiceProvider.instance());
  }

  public Operator(KubernetesClient kubernetesClient) {
    this(kubernetesClient, ConfigurationServiceProvider.instance());
  }

  /**
   * @deprecated Use {@link #Operator(Consumer)} instead
   */
  @Deprecated
  public Operator(ConfigurationService configurationService) {
    this(new DefaultKubernetesClient(), configurationService);
  }

  public Operator(Consumer<ConfigurationServiceOverrider> overrider) {
    this(new DefaultKubernetesClient(), overrider);
  }

  public Operator(KubernetesClient client, Consumer<ConfigurationServiceOverrider> overrider) {
    this.kubernetesClient = client;
    ConfigurationServiceProvider.overrideCurrent(overrider);

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
    ConfigurationServiceProvider.set(configurationService);
  }

  /** Adds a shutdown hook that automatically calls {@link #stop()} when the app shuts down. */
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

      controllers.shouldStart();

      final var version = ConfigurationServiceProvider.instance().getVersion();
      log.info(
          "Operator SDK {} (commit: {}) built on {} starting...",
          version.getSdkVersion(),
          version.getCommit(),
          version.getBuiltTime());

      final var clientVersion = Version.clientVersion();
      log.info("Client version: {}", clientVersion);

      ExecutorServiceManager.init();
      controllers.start();
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

    if (configuration == null) {
      throw new OperatorException(
          "Cannot register reconciler with name " + reconciler.getClass().getCanonicalName() +
              " reconciler named " + ReconcilerUtils.getNameFor(reconciler)
              + " because its configuration cannot be found.\n" +
              " Known reconcilers are: "
              + ConfigurationServiceProvider.instance().getKnownReconcilerNames());
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
    return controllers.get(name).map(RegisteredController.class::cast);
  }

  public Set<RegisteredController> getRegisteredControllers() {
    return new HashSet<>(controllers.controllers());
  }

  public int getRegisteredControllersNumber() {
    return controllers.size();
  }

  private void initLeaderElector(KubernetesClient client) {

    var leaderElectionConfig =
        ConfigurationServiceProvider.instance().getLeaderElectionConfiguration();
    if (leaderElectionConfig.isEmpty()) {
      return;
    }
    var conf = leaderElectionConfig.get();

    // todo discuss openshift client not a NamespacedKubernetesClient?
    // name of the pod
    // todo configurable
    String identity = System.getenv("HOSTNAME");

    Lock lock = new LeaseLock(conf.getLeaseNamespace(), conf.getLeaseName(), identity);
    // todo check release on cancel
    leaderElector = new LeaderElectorBuilder<>((NamespacedKubernetesClient) client)
        .withConfig(new LeaderElectionConfig(lock, conf.getLeaseDuration(), conf.getRenewDeadline(),
            conf.getRetryPeriod(), null, true, conf.getLeaseName()))
        .build();
  }

}
