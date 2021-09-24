package io.javaoperatorsdk.operator.api.config;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.ResourceController;

public class AbstractConfigurationService implements ConfigurationService {
  private final Map<String, ControllerConfiguration> configurations = new ConcurrentHashMap<>();
  private final Version version;

  public AbstractConfigurationService(Version version) {
    this.version = version;
  }

  protected <R extends CustomResource> void register(ControllerConfiguration<R> config) {
    put(config, true);
  }

  protected <R extends CustomResource> void replace(ControllerConfiguration<R> config) {
    put(config, false);
  }

  private <R extends CustomResource> void put(
      ControllerConfiguration<R> config, boolean failIfExisting) {
    final var name = config.getName();
    if (failIfExisting) {
      final var existing = configurations.get(name);
      if (existing != null) {
        throwExceptionOnNameCollision(config.getAssociatedControllerClassName(), existing);
      }
    }
    configurations.put(name, config);
    config.setConfigurationService(this);
  }

  protected void throwExceptionOnNameCollision(
      String newControllerClassName, ControllerConfiguration existing) {
    throw new IllegalArgumentException(
        "Controller name '"
            + existing.getName()
            + "' is used by both "
            + existing.getAssociatedControllerClassName()
            + " and "
            + newControllerClassName);
  }

  @Override
  public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(
      ResourceController<R> controller) {
    final var key = keyFor(controller);
    final var configuration = configurations.get(key);
    if (configuration == null) {
      logMissingControllerWarning(key, getControllersNameMessage());
    }
    return configuration;
  }

  protected void logMissingControllerWarning(String controllerKey,
      String controllersNameMessage) {
    System.out
        .println("Cannot find controller named '" + controllerKey + "'. " + controllersNameMessage);
  }

  private String getControllersNameMessage() {
    return "Known controllers: "
        + getKnownControllerNames().stream().reduce((s, s2) -> s + ", " + s2).orElse("None")
        + ".";
  }

  protected String keyFor(ResourceController controller) {
    return ControllerUtils.getNameFor(controller);
  }

  protected ControllerConfiguration getFor(String controllerName) {
    return configurations.get(controllerName);
  }

  protected Stream<ControllerConfiguration> controllerConfigurations() {
    return configurations.values().stream();
  }

  @Override
  public Set<String> getKnownControllerNames() {
    return configurations.keySet();
  }

  @Override
  public Version getVersion() {
    return version;
  }
}
