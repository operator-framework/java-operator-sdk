package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class WorkflowCleanupResult extends WorkflowResult {

  private final List<DependentResource> deleteCalledOnDependents;
  private final List<DependentResource> postConditionNotMetDependents;

  WorkflowCleanupResult(Map<DependentResource, Exception> erroredDependents,
      List<DependentResource> postConditionNotMet, List<DependentResource> deleteCalled) {
    super(erroredDependents);
    this.deleteCalledOnDependents = deleteCalled;
    this.postConditionNotMetDependents = postConditionNotMet;
  }

  public List<DependentResource> getDeleteCalledOnDependents() {
    return deleteCalledOnDependents;
  }

  public List<DependentResource> getPostConditionNotMetDependents() {
    return postConditionNotMetDependents;
  }

  public boolean allPostConditionsMet() {
    return postConditionNotMetDependents.isEmpty();
  }
}
