package io.javaoperatorsdk.operator.dependent.dependentfilter;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;

@Workflow(dependents = {@Dependent(type = FilteredDependentConfigMap.class)})
@ControllerConfiguration(informer = @Informer(onUpdateFilter = UpdateFilter.class))
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
