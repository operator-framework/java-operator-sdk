package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.javaoperatorsdk.operator.api.reconciler.BaseControl;

public interface ConditionNotFulfilledHandler {
  <T extends BaseControl<T>> T createControl();
}
