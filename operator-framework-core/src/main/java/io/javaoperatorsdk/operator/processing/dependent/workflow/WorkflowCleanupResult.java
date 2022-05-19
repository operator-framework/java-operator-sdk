package io.javaoperatorsdk.operator.processing.dependent.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

@SuppressWarnings("rawtypes")
public class WorkflowCleanupResult {

  private List<DependentResource> deletedDependents = new ArrayList<>();
  private List<DependentResource> notDeletedDependents = new ArrayList<>();
  private Map<DependentResource, Exception> erroredDependents = new HashMap<>();

  public List<DependentResource> getDeletedDependents() {
    return deletedDependents;
  }

  public WorkflowCleanupResult setDeletedDependents(List<DependentResource> deletedDependents) {
    this.deletedDependents = deletedDependents;
    return this;
  }

  public List<DependentResource> getNotDeletedDependents() {
    return notDeletedDependents;
  }

  public WorkflowCleanupResult setNotDeletedDependents(
      List<DependentResource> notDeletedDependents) {
    this.notDeletedDependents = notDeletedDependents;
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
}
