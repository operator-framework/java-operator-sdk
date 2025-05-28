package io.javaoperatorsdk.operator.api.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

/**
 * An abstract implementation of {@link ConfigurationService} meant to ease custom implementations
 */
@SuppressWarnings("rawtypes")
public class AbstractConfigurationService implements ConfigurationService {
  private final Map<String, ControllerConfiguration> configurations = new ConcurrentHashMap<>();
  private final Version version;
  private KubernetesClient client;
  private Cloner cloner;
  private ExecutorServiceManager executorServiceManager;

  protected AbstractConfigurationService(Version version) {
    this(version, null);
  }

  protected AbstractConfigurationService(Version version, Cloner cloner) {
    this(version, cloner, null, null);
  }

  /**
   * Creates a new {@link AbstractConfigurationService} with the specified parameters.
   *
   * @param client the {@link KubernetesClient} instance to use to connect to the cluster, if let
   *     {@code null}, the client will be lazily instantiated with the default configuration
   *     provided by {@link ConfigurationService#getKubernetesClient()} the first time {@link
   *     #getKubernetesClient()} is called
   * @param version the version information
   * @param cloner the {@link Cloner} to use, if {@code null} the default provided by {@link
   *     ConfigurationService#getResourceCloner()} will be used
   * @param executorServiceManager the {@link ExecutorServiceManager} instance to be used, can be
   *     {@code null} to lazily initialize one by default when {@link #getExecutorServiceManager()}
   *     is called
   */
  public AbstractConfigurationService(
      Version version,
      Cloner cloner,
      ExecutorServiceManager executorServiceManager,
      KubernetesClient client) {
    this.version = version;
    init(cloner, executorServiceManager, client);
  }

  /**
   * Subclasses can call this method to more easily initialize the {@link Cloner} and {@link
   * ExecutorServiceManager} associated with this ConfigurationService implementation. This is
   * useful in situations where the cloner depends on a mapper that might require additional
   * configuration steps before it's ready to be used.
   *
   * @param cloner the {@link Cloner} instance to be used, if {@code null}, the default provided by
   *     {@link ConfigurationService#getResourceCloner()} will be used
   * @param executorServiceManager the {@link ExecutorServiceManager} instance to be used, can be
   *     {@code null} to lazily initialize one by default when {@link #getExecutorServiceManager()}
   *     is called
   * @param client the {@link KubernetesClient} instance to use to connect to the cluster, if let
   *     {@code null}, the client will be lazily instantiated with the default configuration
   *     provided by {@link ConfigurationService#getKubernetesClient()} the first time {@link
   *     #getKubernetesClient()} is called
   */
  protected void init(
      Cloner cloner, ExecutorServiceManager executorServiceManager, KubernetesClient client) {
    this.client = client;
    this.cloner = cloner != null ? cloner : ConfigurationService.super.getResourceCloner();
    this.executorServiceManager = executorServiceManager;
  }

  protected <R extends HasMetadata> void register(ControllerConfiguration<R> config) {
    put(config, true);
  }

  protected <R extends HasMetadata> void replace(ControllerConfiguration<R> config) {
    put(config, false);
  }

  @SuppressWarnings("unchecked")
  private <R extends HasMetadata> void put(
      ControllerConfiguration<R> config, boolean failIfExisting) {
    final var name = config.getName();
    if (failIfExisting) {
      final var existing = configurations.get(name);
      if (existing != null) {
        throwExceptionOnNameCollision(config.getAssociatedReconcilerClassName(), existing);
      }
    }
    configurations.put(name, config);
  }

  protected <R extends HasMetadata> void throwExceptionOnNameCollision(
      String newReconcilerClassName, ControllerConfiguration<R> existing) {
    throw new IllegalArgumentException(
        "Reconciler name '"
            + existing.getName()
            + "' is used by both "
            + existing.getAssociatedReconcilerClassName()
            + " and "
            + newReconcilerClassName);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <R extends HasMetadata> ControllerConfiguration<R> getConfigurationFor(
      Reconciler<R> reconciler) {
    final var key = keyFor(reconciler);
    final var configuration = configurations.get(key);
    if (configuration == null) {
      logMissingReconcilerWarning(key, getReconcilersNameMessage());
    }
    return configuration;
  }

  protected void logMissingReconcilerWarning(String reconcilerKey, String reconcilersNameMessage) {
    log.warn("Cannot find reconciler named '{}'. {}", reconcilerKey, reconcilersNameMessage);
  }

  private String getReconcilersNameMessage() {
    return "Known reconcilers: "
        + getKnownReconcilerNames().stream().reduce((s, s2) -> s + ", " + s2).orElse("None")
        + ".";
  }

  protected <R extends HasMetadata> String keyFor(Reconciler<R> reconciler) {
    return ReconcilerUtils.getNameFor(reconciler);
  }

  @SuppressWarnings("unused")
  protected ControllerConfiguration getFor(String reconcilerName) {
    return configurations.get(reconcilerName);
  }

  @SuppressWarnings("unused")
  protected Stream<ControllerConfiguration> controllerConfigurations() {
    return configurations.values().stream();
  }

  @Override
  public Set<String> getKnownReconcilerNames() {
    return configurations.keySet();
  }

  @Override
  public Version getVersion() {
    return version;
  }

  @Override
  public Cloner getResourceCloner() {
    return cloner;
  }

  @Override
  public KubernetesClient getKubernetesClient() {
    // lazy init to avoid needing initializing a client when not needed (in tests, in particular)
    if (client == null) {
      client = ConfigurationService.super.getKubernetesClient();
    }
    return client;
  }

  @Override
  public ExecutorServiceManager getExecutorServiceManager() {
    // lazy init to avoid initializing thread pools for nothing in an overriding scenario
    if (executorServiceManager == null) {
      executorServiceManager = ConfigurationService.super.getExecutorServiceManager();
    }
    return executorServiceManager;
  }
}
