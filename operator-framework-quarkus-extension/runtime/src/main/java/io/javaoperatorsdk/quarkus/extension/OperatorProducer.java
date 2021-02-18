package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.quarkus.arc.DefaultBean;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

@Singleton
public class OperatorProducer {

  @Produces
  @DefaultBean
  @Singleton
  Operator operator(
      KubernetesClient client,
      ConfigurationService configuration,
      Instance<ResourceController<? extends CustomResource>> controllers) {
    final var operator = new Operator(client, configuration);
    for (ResourceController<? extends CustomResource> controller : controllers) {
      ControllerConfiguration<? extends CustomResource> config =
          configuration.getConfigurationFor(controller);
      if (!config.isRegistrationDelayed()) {
        operator.register(controller);
      }
    }
    return operator;
  }
}
