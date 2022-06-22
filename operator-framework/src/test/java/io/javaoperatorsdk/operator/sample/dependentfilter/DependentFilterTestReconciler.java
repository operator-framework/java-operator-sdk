package io.javaoperatorsdk.operator.sample.dependentfilter;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@ControllerConfiguration(onUpdateFilter = UpdateFilter.class,
    dependents = {@Dependent(type = FilteredDependentConfigMap.class)})
public class DependentFilterTestReconciler
    implements Reconciler<DependentFilterTestCustomResource> {

  public static final String CONFIG_MAP_FILTER_VALUE = "config_map_skip_this";
  public static final String CM_VALUE_KEY = "value";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<DependentFilterTestCustomResource> reconcile(
      DependentFilterTestCustomResource resource,
      Context<DependentFilterTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
