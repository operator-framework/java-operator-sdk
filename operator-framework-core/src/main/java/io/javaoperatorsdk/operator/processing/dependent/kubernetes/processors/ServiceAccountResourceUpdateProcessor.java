package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.ServiceAccount;

public class ServiceAccountResourceUpdateProcessor
    extends GenericResourceUpdatePreProcessor<ServiceAccount> {

  @Override
  protected void updateClonedActual(ServiceAccount actual, ServiceAccount desired) {
    actual.setAutomountServiceAccountToken(desired.getAutomountServiceAccountToken());
    actual.setImagePullSecrets(desired.getImagePullSecrets());
    actual.setSecrets(desired.getSecrets());
  }

  @Override
  public boolean matches(ServiceAccount actual, ServiceAccount desired, boolean equality) {
    return Objects.equals(actual.getAutomountServiceAccountToken(),
        desired.getAutomountServiceAccountToken()) &&
        Objects.equals(actual.getImagePullSecrets(), desired.getImagePullSecrets()) &&
        Objects.equals(actual.getSecrets(), desired.getSecrets());
  }
}
