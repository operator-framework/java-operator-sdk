package io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.Endpoints;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class EndpointsResourceUpdaterMatcher extends GenericResourceUpdaterMatcher<Endpoints> {

  @Override
  protected void updateClonedActual(Endpoints actual, Endpoints desired) {
    actual.setSubsets(desired.getSubsets());
  }

  @Override
  public boolean matches(Endpoints actual, Endpoints desired, Context<?> context) {
    return Objects.equals(actual.getSubsets(), desired.getSubsets());
  }

}
