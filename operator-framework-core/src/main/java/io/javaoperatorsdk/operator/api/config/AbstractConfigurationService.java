package io.javaoperatorsdk.operator.api.config;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.ResourceController;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractConfigurationService implements ConfigurationService {

  protected final Map<String, ControllerConfiguration> configurations = new ConcurrentHashMap<>();

  protected <R extends CustomResource> void register(ControllerConfiguration<R> config) {
    final var name = config.getName();
    final var existing = configurations.get(name);
    if (existing != null) {
      throw new IllegalArgumentException(
          "Controller name '"
              + name
              + "' is used by both "
              + existing.getAssociatedControllerClassName()
              + " and "
              + config.getAssociatedControllerClassName());
    }
    configurations.put(name, config);
  }

  @Override
  public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(
      ResourceController<R> controller) {
    return configurations.get(ControllerUtils.getNameFor(controller));
  }
}
