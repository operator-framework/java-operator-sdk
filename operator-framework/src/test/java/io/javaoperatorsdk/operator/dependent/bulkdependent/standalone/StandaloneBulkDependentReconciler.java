package io.javaoperatorsdk.operator.dependent.bulkdependent.standalone;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.CRUDConfigMapBulkDependentResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class StandaloneBulkDependentReconciler
    implements Reconciler<BulkDependentTestCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final ConfigMapDeleterBulkDependentResource dependent;

  public StandaloneBulkDependentReconciler() {
    dependent = new CRUDConfigMapBulkDependentResource();
  }

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    dependent.reconcile(resource, context);

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, BulkDependentTestCustomResource>> prepareEventSources(
      EventSourceContext<BulkDependentTestCustomResource> context) {
    return List.of(dependent.initEventSource(context));
  }
}
