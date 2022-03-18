package io.javaoperatorsdk.operator.sample.simple;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class DuplicateCRController implements Reconciler<TestCustomResource> {

  @Override
  public UpdateControl<TestCustomResource> reconcile(TestCustomResource resource,
      Context<TestCustomResource> context) {
    return UpdateControl.noUpdate();
  }
}
