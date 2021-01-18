package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkus.arc.DefaultBean;
import io.quarkus.runtime.ShutdownEvent;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class OperatorProducer {

  @Inject Instance<ResourceController<? extends CustomResource>> controllers;

  @Produces
  @DefaultBean
  @Singleton
  Operator operator(KubernetesClient client, ConfigurationService configuration) {
    final var operator = new Operator(client, configuration);
    controllers.stream().forEach(operator::register);
    return operator;
  }

  void onStop(@Observes ShutdownEvent ev, Operator operator) {
    operator.close();
  }

}
