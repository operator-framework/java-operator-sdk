package io.javaoperatorsdk.operator.processing.dependent.waitfor;

import io.javaoperatorsdk.operator.api.reconciler.BaseControl;

public interface ConditionNotFulfilledHandler<P extends BaseControl<P>> {

  BaseControl<P> control();

}
