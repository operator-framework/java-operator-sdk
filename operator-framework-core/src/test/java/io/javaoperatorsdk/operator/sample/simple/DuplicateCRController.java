package io.javaoperatorsdk.operator.sample.simple;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Controller;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@Controller
public class DuplicateCRController implements Reconciler<TestCustomResource> {

  @Override
  public UpdateControl<TestCustomResource> reconcile(TestCustomResource resource,
      Context context) {
    return UpdateControl.noUpdate();
  }
}
