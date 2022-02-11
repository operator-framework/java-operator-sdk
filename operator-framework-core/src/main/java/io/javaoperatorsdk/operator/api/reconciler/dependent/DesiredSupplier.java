package io.javaoperatorsdk.operator.api.reconciler.dependent;

import io.javaoperatorsdk.operator.api.reconciler.Context;

@FunctionalInterface
public interface DesiredSupplier<R, P> {

  R getDesired(P primary, Context context);

}
