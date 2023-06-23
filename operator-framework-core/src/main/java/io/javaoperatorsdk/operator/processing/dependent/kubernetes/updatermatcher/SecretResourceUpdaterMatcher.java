package io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class SecretResourceUpdaterMatcher extends GenericResourceUpdaterMatcher<Secret> {

  @Override
  protected void updateClonedActual(Secret actual, Secret desired) {
    actual.setData(desired.getData());
    actual.setStringData(desired.getStringData());
    actual.setImmutable(desired.getImmutable());
    actual.setType(desired.getType());
  }

  @Override
  public boolean matches(Secret actual, Secret desired, Context<?> context) {
    return Objects.equals(actual.getImmutable(), desired.getImmutable()) &&
        Objects.equals(actual.getType(), desired.getType()) &&
        Objects.equals(actual.getData(), desired.getData()) &&
        Objects.equals(actual.getStringData(), desired.getStringData());
  }
}
