package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;

public class Operator extends AbstractOperator {
  public Operator(KubernetesClient k8sClient, ConfigurationService configurationService) {
    super(k8sClient, configurationService);
  }
}
