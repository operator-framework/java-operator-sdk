package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.AggregatedOperatorException;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class WorkflowCleanupResult {

  private List<DependentResource> deleteCalledOnDependents = new ArrayList<>();
  private List<DependentResource> postConditionNotMetDependents = new ArrayList<>();
  private Map<DependentResource, Exception> erroredDependents = new HashMap<>();

  public List<DependentResource> getDeleteCalledOnDependents() {
    return deleteCalledOnDependents;
  }

  public WorkflowCleanupResult setDeleteCalledOnDependents(
      List<DependentResource> deletedDependents) {
    this.deleteCalledOnDependents = deletedDependents;
    return this;
  }

  public List<DependentResource> getPostConditionNotMetDependents() {
    return postConditionNotMetDependents;
  }

  public WorkflowCleanupResult setPostConditionNotMetDependents(
      List<DependentResource> postConditionNotMetDependents) {
    this.postConditionNotMetDependents = postConditionNotMetDependents;
    return this;
  }

  public Map<DependentResource, Exception> getErroredDependents() {
    return erroredDependents;
  }

  public WorkflowCleanupResult setErroredDependents(
      Map<DependentResource, Exception> erroredDependents) {
    this.erroredDependents = erroredDependents;
    return this;
  }

  public boolean allPostConditionsMet() {
    return postConditionNotMetDependents.isEmpty();
  }

  public boolean erroredDependentsExists() {
    return !erroredDependents.isEmpty();
  }

  public void throwAggregateExceptionIfErrorsPresent() {
    if (erroredDependentsExists()) {
      throw new AggregatedOperatorException("Exception(s) during workflow execution.",
          new ArrayList<>(erroredDependents.values()));
    }
  }


}
