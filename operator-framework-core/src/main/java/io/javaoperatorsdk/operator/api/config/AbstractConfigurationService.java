package io.javaoperatorsdk.operator.api.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("rawtypes")
public class AbstractConfigurationService implements ConfigurationService {
  private final Map<String, ControllerConfiguration> configurations = new ConcurrentHashMap<>();
  private final Version version;
  private Cloner cloner;
  private ObjectMapper mapper;
  private ExecutorServiceManager executorServiceManager;

  public AbstractConfigurationService(Version version) {
    this(version, null, null, null);
  }

  public AbstractConfigurationService(Version version, Cloner cloner) {
    this(version, cloner, null, null);
  }

  public AbstractConfigurationService(Version version, Cloner cloner, ObjectMapper mapper,
      ExecutorServiceManager executorServiceManager) {
    this.version = version;
    init(cloner, mapper, executorServiceManager);
  }

  /**
   * Subclasses can call this method to more easily initialize the {@link Cloner}
   * {@link ObjectMapper} and {@link ExecutorServiceManager} associated with this
   * ConfigurationService implementation. This is useful in situations where the cloner depends on a
   * mapper that might require additional configuration steps before it's ready to be used.
   *
   * @param cloner the {@link Cloner} instance to be used
   * @param mapper the {@link ObjectMapper} instance to be used
   * @param executorServiceManager the {@link ExecutorServiceManager} instance to be used
   */
  protected void init(Cloner cloner, ObjectMapper mapper,
      ExecutorServiceManager executorServiceManager) {
    this.cloner = cloner != null ? cloner : ConfigurationService.super.getResourceCloner();
    this.mapper = mapper != null ? mapper : ConfigurationService.super.getObjectMapper();
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
  public ObjectMapper getObjectMapper() {
    return mapper;
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
