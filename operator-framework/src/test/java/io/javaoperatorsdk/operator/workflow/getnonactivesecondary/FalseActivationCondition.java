package io.javaoperatorsdk.operator.workflow.getnonactivesecondary;

import io.fabric8.openshift.api.model.Route;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class FalseActivationCondition
    implements Condition<Route, GetNonActiveSecondaryCustomResource> {
  @Override
  public boolean isMet(
      DependentResource<Route, GetNonActiveSecondaryCustomResource> dependentResource,
      GetNonActiveSecondaryCustomResource primary,
      Context<GetNonActiveSecondaryCustomResource> context) {
    return false;
  }
}
