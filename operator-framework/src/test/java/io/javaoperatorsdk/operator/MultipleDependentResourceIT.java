package io.javaoperatorsdk.operator;

import io.javaoperatorsdk.operator.sample.multipledependentresource.MultipleDependentResourceReconciler;

class MultipleDependentResourceIT
    extends AbstractMultipleDependentResourceIT<MultipleDependentResourceReconciler> {

  @Override
  protected Class<MultipleDependentResourceReconciler> getReconcilerClass() {
    return MultipleDependentResourceReconciler.class;
  }
}
