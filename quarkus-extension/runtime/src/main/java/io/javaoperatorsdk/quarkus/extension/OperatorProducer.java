package io.javaoperatorsdk.quarkus.extension;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;
import io.quarkus.arc.DefaultBean;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

@Singleton
public class OperatorProducer {

  @Produces
  @DefaultBean
  @Singleton
  Operator operator(KubernetesClient client, ConfigurationService configuration) {
    return new Operator(client, configuration);
  }
}
