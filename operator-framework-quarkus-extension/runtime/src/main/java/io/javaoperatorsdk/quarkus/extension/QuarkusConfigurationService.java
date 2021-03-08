package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.config.Version;
import io.quarkus.arc.runtime.ClientProxyUnwrapper;
import java.util.List;

public class QuarkusConfigurationService extends AbstractConfigurationService {
  private static final ClientProxyUnwrapper unwrapper = new ClientProxyUnwrapper();
  private final KubernetesClient client;
  private final boolean checkCRDAndValidateLocalModel;

  public QuarkusConfigurationService(
      Version version,
      List<ControllerConfiguration> configurations,
      KubernetesClient client,
      boolean checkCRDAndValidateLocalModel) {
    super(version);
    this.client = client;
    if (configurations != null && !configurations.isEmpty()) {
      configurations.forEach(this::register);
    }
    this.checkCRDAndValidateLocalModel = checkCRDAndValidateLocalModel;
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

  @Override
  public boolean checkCRDAndValidateLocalModel() {
    return checkCRDAndValidateLocalModel;
  }

  private static <R extends CustomResource> ResourceController<R> unwrap(
      ResourceController<R> controller) {
    return (ResourceController<R>) unwrapper.apply(controller);
  }
}
