package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
class WorkflowResult {

  private final Map<DependentResource, Exception> erroredDependents;

  WorkflowResult(Map<DependentResource, Exception> erroredDependents) {
    this.erroredDependents = erroredDependents;
  }

  public Map<DependentResource, Exception> getErroredDependents() {
    return erroredDependents;
  }

  /**
   * @deprecated Use {@link #erroredDependentsExist()} instead
   */
  @Deprecated(forRemoval = true)
  public boolean erroredDependentsExists() {
    return !erroredDependents.isEmpty();
  }

  @SuppressWarnings("unused")
  public boolean erroredDependentsExist() {
    return !erroredDependents.isEmpty();
  }

  public void throwAggregateExceptionIfErrorsPresent() {
    if (erroredDependentsExist()) {
      throw new AggregatedOperatorException("Exception(s) during workflow execution.",
          erroredDependents.entrySet().stream()
              .collect(Collectors.toMap(e -> e.getKey().getClass().getName(), Entry::getValue)));
    }
  }
}
