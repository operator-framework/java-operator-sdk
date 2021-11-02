package io.javaoperatorsdk.operator.sample.simple;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.Reconciler;
import io.javaoperatorsdk.operator.api.UpdateControl;

@Controller
public class TestCustomReconcilerV2 implements Reconciler<TestCustomResourceV2> {

  @Override
  public UpdateControl<TestCustomResourceV2> createOrUpdateResources(TestCustomResourceV2 resource,
      Context context) {
    return UpdateControl.noUpdate();
  }
}
