package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.quarkus.arc.runtime.ClientProxyUnwrapper;
import java.util.List;

public class QuarkusConfigurationService extends AbstractConfigurationService {
  private static final ClientProxyUnwrapper unwrapper = new ClientProxyUnwrapper();
  private final KubernetesClient client;

  public QuarkusConfigurationService(
      List<ControllerConfiguration> configurations, KubernetesClient client) {
    this.client = client;
    if (configurations != null && !configurations.isEmpty()) {
      configurations.forEach(this::register);
    }
  }

  @Override
  public Config getClientConfiguration() {
    return client.getConfiguration();
  }

  @Override
  public <R extends CustomResource> ControllerConfiguration<R> getConfigurationFor(
      ResourceController<R> controller) {
    final var unwrapped = unwrap(controller);
    return super.getConfigurationFor(unwrapped);
  }

  private static <R extends CustomResource> ResourceController<R> unwrap(
      ResourceController<R> controller) {
    return (ResourceController<R>) unwrapper.apply(controller);
  }
}
