package io.javaoperatorsdk.operator.sample.simple;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.Reconciler;
import io.javaoperatorsdk.operator.api.UpdateControl;

@Controller
public class DuplicateCRController implements Reconciler<TestCustomResource> {

  @Override
  public UpdateControl<TestCustomResource> createOrUpdateResources(TestCustomResource resource,
      Context context) {
    return UpdateControl.noUpdate();
  }
}
