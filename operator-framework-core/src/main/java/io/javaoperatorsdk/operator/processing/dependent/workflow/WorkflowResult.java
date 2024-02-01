package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
class WorkflowResult {

  public static final String NUMBER_DELIMITER = "_";
  private final Map<DependentResource, Exception> erroredDependents;

  WorkflowResult(Map<DependentResource, Exception> erroredDependents) {
    this.erroredDependents = erroredDependents;
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
      Map<String, Exception> exceptionMap = new HashMap<>();
      Map<String, Integer> numberOfClasses = new HashMap<>();

      for (Entry<DependentResource, Exception> entry : erroredDependents.entrySet()) {
        String name = entry.getKey().getClass().getName();
        var num = numberOfClasses.getOrDefault(name, 0);
        if (num > 0) {
          exceptionMap.put(name + NUMBER_DELIMITER + num, entry.getValue());
        } else {
          exceptionMap.put(name, entry.getValue());
        }
        numberOfClasses.put(name, num + 1);
      }

      throw new AggregatedOperatorException("Exception(s) during workflow execution.",
          exceptionMap);
    }
  }
}
