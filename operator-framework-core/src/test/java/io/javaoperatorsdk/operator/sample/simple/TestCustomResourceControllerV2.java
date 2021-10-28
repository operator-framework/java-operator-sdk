package io.javaoperatorsdk.operator.sample.simple;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

@Controller
public class TestCustomResourceControllerV2 implements ResourceController<TestCustomResourceV2> {

  @Override
  public UpdateControl<TestCustomResourceV2> createOrUpdateResource(TestCustomResourceV2 resource,
      Context<TestCustomResourceV2> context) {
    return UpdateControl.noUpdate();
  }
}
