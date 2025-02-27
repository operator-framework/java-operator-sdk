package io.javaoperatorsdk.operator.sample.simple;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class TestCustomReconcilerOtherV1 implements Reconciler<TestCustomResourceOtherV1> {

  @Override
  public UpdateControl<TestCustomResourceOtherV1> reconcile(
      TestCustomResourceOtherV1 resource, Context<TestCustomResourceOtherV1> context) {
    return UpdateControl.noUpdate();
  }
}
