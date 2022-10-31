package io.javaoperatorsdk.operator.sample.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentCustomResource;

public class StatefulSetReadyCondition
    implements Condition<StatefulSet, ComplexDependentCustomResource> {

  @Override
  public boolean isMet(ComplexDependentCustomResource primary, StatefulSet secondary,
      Context<ComplexDependentCustomResource> context) {

    var readyReplicas = secondary.getStatus().getReadyReplicas();
    return readyReplicas != null && readyReplicas > 0;
  }
}
