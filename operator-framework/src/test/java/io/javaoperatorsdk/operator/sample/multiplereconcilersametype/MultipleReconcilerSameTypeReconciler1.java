package io.javaoperatorsdk.operator.sample.multiplereconcilersametype;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;
import java.util.concurrent.atomic.AtomicInteger;

@ControllerConfiguration(labelSelector = "reconciler = 1")
public class MultipleReconcilerSameTypeReconciler1
    implements Reconciler<MultipleReconcilerSameTypeCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<MultipleReconcilerSameTypeCustomResource> reconcile(
      MultipleReconcilerSameTypeCustomResource resource,
      Context<MultipleReconcilerSameTypeCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    resource.setStatus(new MultipleReconcilerSameTypeStatus());
    resource.getStatus().setReconciledBy(getClass().getSimpleName());
    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

}
