package io.javaoperatorsdk.operator.baseapi.expectation.simple;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class SimpleExpectationReconciler implements Reconciler<SimpleExpectationCustomResource> {

  @Override
  public UpdateControl<SimpleExpectationCustomResource> reconcile(
      SimpleExpectationCustomResource resource, Context<SimpleExpectationCustomResource> context) {

    return UpdateControl.noUpdate();
  }
}
