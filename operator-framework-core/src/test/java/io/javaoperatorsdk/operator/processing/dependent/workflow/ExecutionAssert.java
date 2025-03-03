package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.assertj.core.api.AbstractAssert;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class ExecutionAssert extends AbstractAssert<ExecutionAssert, List<ReconcileRecord>> {

  public ExecutionAssert(List<ReconcileRecord> reconcileRecords) {
    super(reconcileRecords, ExecutionAssert.class);
  }

  public static ExecutionAssert assertThat(List<ReconcileRecord> actual) {
    return new ExecutionAssert(actual);
  }

  public ExecutionAssert reconciled(DependentResource<?, ?>... dependentResources) {
    for (int i = 0; i < dependentResources.length; i++) {
      final DependentResource<?, ?> dr = dependentResources[i];
      var rr = getReconcileRecordFor(dr);
      if (rr.isEmpty()) {
        failWithMessage("Resource not reconciled: %s with index %d", dr, i);
      } else {
        if (rr.get().isDeleted()) {
          failWithMessage("Resource deleted: %s with index %d", dr, i);
        }
      }
    }
    return this;
  }

  public ExecutionAssert deleted(DependentResource<?, ?>... dependentResources) {
    for (int i = 0; i < dependentResources.length; i++) {
      final DependentResource<?, ?> dr = dependentResources[i];
      var rr = getReconcileRecordFor(dr);
      if (rr.isEmpty()) {
        failWithMessage("Resource not reconciled: %s with index %d", dr, i);
      } else {
        if (!rr.get().isDeleted()) {
          failWithMessage("Resource not deleted: %s with index %d", dr, i);
        }
      }
    }
    return this;
  }

  private List<DependentResource> getActualDependentResources() {
    return actual.stream().map(ReconcileRecord::getDependentResource).collect(Collectors.toList());
  }

  private Optional<ReconcileRecord> getReconcileRecordFor(DependentResource dependentResource) {
    return actual.stream().filter(rr -> rr.getDependentResource() == dependentResource).findFirst();
  }

  public ExecutionAssert reconciledInOrder(DependentResource<?, ?>... dependentResources) {
    if (dependentResources.length < 2) {
      throw new IllegalArgumentException("At least two dependent resource needs to be specified");
    }
    for (int i = 0; i < dependentResources.length - 1; i++) {
      checkIfReconciled(i, dependentResources);
      checkIfReconciled(i + 1, dependentResources);
      if (getActualDependentResources().indexOf(dependentResources[i])
          > getActualDependentResources().indexOf(dependentResources[i + 1])) {
        failWithMessage(
            "Dependent resource on index %d reconciled after the one on index %d", i, i + 1);
      }
    }

    return this;
  }

  public ExecutionAssert notReconciled(DependentResource<?, ?>... dependentResources) {
    for (int i = 0; i < dependentResources.length; i++) {
      final DependentResource<?, ?> dr = dependentResources[i];
      if (getActualDependentResources().contains(dr)) {
        failWithMessage("Resource was reconciled: %s with index %d", dr, i);
      }
    }
    return this;
  }

  private void checkIfReconciled(int i, DependentResource<?, ?>[] dependentResources) {
    final DependentResource<?, ?> dr = dependentResources[i];
    if (!getActualDependentResources().contains(dr)) {
      failWithMessage("Dependent resource: %s, not reconciled on place %d", dr, i);
    }
  }
}
