package io.javaoperatorsdk.operator.sample.complexdependent.dependent;

import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.javaoperatorsdk.operator.sample.complexdependent.ComplexDependentCustomResource;

public class StatefulSetReadyCondition
    implements Condition<StatefulSet, ComplexDependentCustomResource> {

  @Override
  public boolean isMet(
      DependentResource<StatefulSet, ComplexDependentCustomResource> dependentResource,
      ComplexDependentCustomResource primary,
      Context<ComplexDependentCustomResource> context) {

    return dependentResource.getSecondaryResource(primary, context).map(secondary -> {
      var readyReplicas = secondary.getStatus().getReadyReplicas();
      return readyReplicas != null && readyReplicas > 0;
    })
        .orElse(false);
  }
}
