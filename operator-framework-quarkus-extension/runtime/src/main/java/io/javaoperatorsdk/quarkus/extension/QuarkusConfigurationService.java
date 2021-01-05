package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.AbstractConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import java.util.List;
import javax.inject.Inject;

public class QuarkusConfigurationService extends AbstractConfigurationService {
  @Inject KubernetesClient client;

  public QuarkusConfigurationService(List<ControllerConfiguration> configurations) {
    if (configurations != null && !configurations.isEmpty()) {
      configurations.forEach(this::register);
    }
  }

  @Override
  public Config getClientConfiguration() {
    return client.getConfiguration();
  }
}
