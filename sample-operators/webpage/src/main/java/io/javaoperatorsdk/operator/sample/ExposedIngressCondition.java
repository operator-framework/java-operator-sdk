package io.javaoperatorsdk.operator.sample;

import java.util.Optional;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class ExposedIngressCondition implements Condition<Ingress, WebPage> {
  @Override
  public boolean isMet(WebPage primary, Optional<Ingress> secondary, Context<WebPage> context) {
    return primary.getSpec().getExposed();
  }
}
