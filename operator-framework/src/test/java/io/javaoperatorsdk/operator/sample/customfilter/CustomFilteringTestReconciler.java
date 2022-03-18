package io.javaoperatorsdk.operator.sample.customfilter;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(eventFilters = {CustomFlagFilter.class, CustomFlagFilter2.class})
public class CustomFilteringTestReconciler implements Reconciler<CustomFilteringTestResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<CustomFilteringTestResource> reconcile(CustomFilteringTestResource resource,
      Context<CustomFilteringTestResource> context) {
    numberOfExecutions.incrementAndGet();
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
