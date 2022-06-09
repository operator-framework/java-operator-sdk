package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ExposedIngressCondition implements Condition<Ingress, WebPage> {
  @Override
  public boolean isMet(DependentResource<Ingress, WebPage> dependentResource, WebPage primary,
      Context<WebPage> context) {
    return primary.getSpec().getExposed();
  }
}
