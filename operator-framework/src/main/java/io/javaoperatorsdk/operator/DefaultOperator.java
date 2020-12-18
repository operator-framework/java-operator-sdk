package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.config.ConfigurationService;

public class DefaultOperator extends AbstractOperator {
  public DefaultOperator(KubernetesClient k8sClient, ConfigurationService configurationService) {
    super(k8sClient, configurationService);
  }
}
