package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
class WorkflowResult {

  private final Map<DependentResource, Exception> erroredDependents;

  WorkflowResult(Map<DependentResource, Exception> erroredDependents) {
    this.erroredDependents = erroredDependents != null ? erroredDependents : Collections.emptyMap();
  }

  public Map<DependentResource, Exception> getErroredDependents() {
    return erroredDependents;
  }

  /**
   * @deprecated Use {@link #erroredDependentsExist()} instead
   * @return if any dependents are in error state
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
              .collect(Collectors.toMap(e -> e.getKey().name(), Entry::getValue)));
    }
  }
}
