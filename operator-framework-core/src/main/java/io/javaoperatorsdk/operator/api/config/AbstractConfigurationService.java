package io.javaoperatorsdk.operator.api.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;

@SuppressWarnings("rawtypes")
public class AbstractConfigurationService implements ConfigurationService {
  private final Map<String, ControllerConfiguration> configurations = new ConcurrentHashMap<>();
  private final Version version;

  public AbstractConfigurationService(Version version) {
    this.version = version;
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
    System.out
        .println("Cannot find reconciler named '" + reconcilerKey + "'. " + reconcilersNameMessage);
  }

  private String getReconcilersNameMessage() {
    return "Known reconcilers: "
        + getKnownReconcilerNames().stream().reduce((s, s2) -> s + ", " + s2).orElse("None")
        + ".";
  }

  protected <R extends HasMetadata> String keyFor(Reconciler<R> reconciler) {
    return ReconcilerUtils.getNameFor(reconciler);
  }

  protected ControllerConfiguration getFor(String reconcilerName) {
    return configurations.get(reconcilerName);
  }

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
}
