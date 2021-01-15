package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractConfigurationService implements ConfigurationService {

  private final Map<String, ControllerConfiguration> configurations = new ConcurrentHashMap<>();

  protected <R extends CustomResource> void register(ControllerConfiguration<R> config) {
    final var name = config.getName();
    final var existing = configurations.get(name);
    if (existing != null) {
      throwExceptionOnNameCollision(config.getAssociatedControllerClassName(), existing);
    }
    configurations.put(name, config);
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
    return configurations.get(ControllerUtils.getNameFor(controller));
  }

  @Override
  public Set<String> getKnownControllerNames() {
    return configurations.keySet();
  }
}
