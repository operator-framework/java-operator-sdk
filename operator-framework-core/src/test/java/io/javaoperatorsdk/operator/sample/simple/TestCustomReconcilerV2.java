package io.javaoperatorsdk.operator.sample.simple;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Controller;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@Controller
public class TestCustomReconcilerV2 implements Reconciler<TestCustomResourceV2> {

  @Override
  public UpdateControl<TestCustomResourceV2> reconcile(TestCustomResourceV2 resource,
      Context context) {
    return UpdateControl.noUpdate();
  }
}
