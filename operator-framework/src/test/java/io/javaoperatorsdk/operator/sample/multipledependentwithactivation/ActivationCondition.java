package io.javaoperatorsdk.operator.sample.multipledependentwithactivation;

import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ActivationCondition
    implements Condition<Route, MultipleDependentActivationCustomResource> {

  public static volatile boolean MET = false;

  @Override
  public boolean isMet(
      DependentResource<Route, MultipleDependentActivationCustomResource> dependentResource,
      MultipleDependentActivationCustomResource primary,
      Context<MultipleDependentActivationCustomResource> context) {
    return MET;
  }
}
