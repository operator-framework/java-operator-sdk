package io.javaoperatorsdk.operator.config.runtime;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultConfigurationService implements ConfigurationService {

  private final Map<String, ControllerConfiguration> configurations = new ConcurrentHashMap<>();

  public static final ConfigurationService INSTANCE = new DefaultConfigurationService();

  @Override
  public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(
      ResourceController<R> controller) {
    if (controller == null) {
      return null;
    }
    final var name = controller.getName();
    var configuration = configurations.get(name);
    if (configuration == null) {
      configuration = new AnnotationConfiguration(controller);
      configurations.put(name, configuration);
    }
    return configuration;
  }
}
