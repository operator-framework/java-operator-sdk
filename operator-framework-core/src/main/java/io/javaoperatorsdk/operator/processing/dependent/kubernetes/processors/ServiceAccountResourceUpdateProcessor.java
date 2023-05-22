package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.ServiceAccount;

public class ServiceAccountResourceUpdateProcessor
    extends GenericResourceUpdatePreProcessor<ServiceAccount> {

  @Override
  protected void updateClonedActual(ServiceAccount actual, ServiceAccount desired) {
    actual.setAutomountServiceAccountToken(desired.getAutomountServiceAccountToken());
    actual.setImagePullSecrets(desired.getImagePullSecrets());
    actual.setSecrets(desired.getSecrets());
  }
}
