package io.javaoperatorsdk.operator.sample.simple;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

@Controller
public class DuplicateCRController implements ResourceController<TestCustomResource> {

  @Override
  public UpdateControl<TestCustomResource> createOrUpdateResource(TestCustomResource resource,
      Context context) {
    return UpdateControl.noUpdate();
  }
}
