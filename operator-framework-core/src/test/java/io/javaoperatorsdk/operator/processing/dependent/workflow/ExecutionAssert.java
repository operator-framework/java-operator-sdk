package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;

import org.assertj.core.api.AbstractAssert;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class ExecutionAssert
    extends AbstractAssert<ExecutionAssert, List<DependentResource<?, ?>>> {

  public ExecutionAssert(List<DependentResource<?, ?>> dependentResources) {
    super(dependentResources, ExecutionAssert.class);
  }

  public static ExecutionAssert assertThat(List<DependentResource<?, ?>> actual) {
    return new ExecutionAssert(actual);
  }

  public ExecutionAssert reconciled(DependentResource<?, ?>... dependentResources) {
    for (int i = 0; i < dependentResources.length; i++) {
      if (!actual.contains(dependentResources[i])) {
        failWithMessage("Resource not reconciled: %s with index %d", dependentResources, i);
      }
    }
    return this;
  }

  public ExecutionAssert reconciledInOrder(DependentResource<?, ?>... dependentResources) {
    if (dependentResources.length < 2) {
      throw new IllegalArgumentException("At least two dependent resource needs to be specified");
    }
    for (int i = 0; i < dependentResources.length - 1; i++) {
      checkIfReconciled(i, dependentResources);
      checkIfReconciled(i + 1, dependentResources);
      if (actual.indexOf(dependentResources[i]) > actual.indexOf(dependentResources[i + 1])) {
        failWithMessage(
            "Dependent resource on index %d reconciled after the one on index %d", i, i + 1);
      }
    }

    return this;
  }

  public ExecutionAssert notReconciled(DependentResource<?, ?>... dependentResources) {
    for (int i = 0; i < dependentResources.length - 1; i++) {
      if (!actual.contains(dependentResources[i])) {
        failWithMessage("Resource was reconciled: %s with index %d", dependentResources, i);
      }
    }
    return this;
  }

  private void checkIfReconciled(int i, DependentResource<?, ?>[] dependentResources) {
    if (!actual.contains(dependentResources[i])) {
      failWithMessage("Dependent resource not reconciled on place %i", i);
    }
  }
}
