package io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class EndpointSliceResourceUpdateMatcher
    extends GenericResourceUpdaterMatcher<EndpointSlice> {

  @Override
  protected void updateClonedActual(EndpointSlice actual, EndpointSlice desired) {
    actual.setEndpoints(desired.getEndpoints());
    actual.setAddressType(desired.getAddressType());
    actual.setPorts(desired.getPorts());
  }

  @Override
  public boolean matches(EndpointSlice actual, EndpointSlice desired, Context<?> context) {
    return Objects.equals(actual.getEndpoints(), desired.getEndpoints()) &&
        Objects.equals(actual.getAddressType(), desired.getAddressType()) &&
        Objects.equals(actual.getPorts(), desired.getPorts());
  }

}
