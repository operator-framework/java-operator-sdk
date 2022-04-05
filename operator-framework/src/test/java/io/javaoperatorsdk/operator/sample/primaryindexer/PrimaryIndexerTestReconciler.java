package io.javaoperatorsdk.operator.sample.primaryindexer;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.EventSourceProvider;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class PrimaryIndexerTestReconciler
    implements Reconciler<PrimaryIndexerTestCustomResource>,
    TestExecutionInfoProvider, EventSourceProvider<PrimaryIndexerTestCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);


  @Override
  public EventSource initEventSource(EventSourceContext<PrimaryIndexerTestCustomResource> context) {


    return null;
  }

  @Override
  public UpdateControl<PrimaryIndexerTestCustomResource> reconcile(
      PrimaryIndexerTestCustomResource resource,
      Context<PrimaryIndexerTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
