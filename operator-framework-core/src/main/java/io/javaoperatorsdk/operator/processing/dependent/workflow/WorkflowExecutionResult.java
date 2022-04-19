package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class WorkflowExecutionResult {

  // this includes also possibly deleted ones
  private List<DependentResource<?, ?>> reconciledDependents = new ArrayList<>();
  private List<DependentResource<?, ?>> notReconciledDependents = new ArrayList<>();
  private List<DependentResource<?, ?>> notReadyDependents = new ArrayList<>();
  private Map<DependentResource<?, ?>, Exception> erroredDependents = new HashMap<>();

  public Map<DependentResource<?, ?>, Exception> getErroredDependents() {
    return erroredDependents;
  }

  public WorkflowExecutionResult setErroredDependents(
      Map<DependentResource<?, ?>, Exception> erroredDependents) {
    this.erroredDependents = erroredDependents;
    return this;
  }

  public List<DependentResource<?, ?>> getReconciledDependents() {
    return reconciledDependents;
  }

  public WorkflowExecutionResult setReconciledDependents(
      List<DependentResource<?, ?>> reconciledDependents) {
    this.reconciledDependents = reconciledDependents;
    return this;
  }

  public List<DependentResource<?, ?>> getNotReadyDependents() {
    return notReadyDependents;
  }

  public WorkflowExecutionResult setNotReadyDependents(
      List<DependentResource<?, ?>> notReadyDependents) {
    this.notReadyDependents = notReadyDependents;
    return this;
  }

  public List<DependentResource<?, ?>> getNotReconciledDependents() {
    return notReconciledDependents;
  }

  public WorkflowExecutionResult setNotReconciledDependents(
      List<DependentResource<?, ?>> notReconciledDependents) {
    this.notReconciledDependents = notReconciledDependents;
    return this;
  }

  public void throwAggregatedExceptionIfErrorsPresent() {
    if (!erroredDependents.isEmpty()) {
      throw createFinalException();
    }
  }

  private AggregatedOperatorException createFinalException() {
    return new AggregatedOperatorException("Exception during workflow.",
        new ArrayList<>(erroredDependents.values()));
  }
}
