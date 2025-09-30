package io.javaoperatorsdk.operator.baseapi.expectation;

import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.expectation.ExpectationManager;

public class ExpectationReconciler implements Reconciler<ExpectationCustomResource> {

  ExpectationManager<ExpectationCustomResource> expectationManager = new ExpectationManager<>();

  @Override
  public UpdateControl<ExpectationCustomResource> reconcile(
      ExpectationCustomResource resource, Context<ExpectationCustomResource> context) {

    return UpdateControl.noUpdate();
  }

  @Override
  public List<EventSource<?, ExpectationCustomResource>> prepareEventSources(
      EventSourceContext<ExpectationCustomResource> context) {
    return List.of();
  }
}
