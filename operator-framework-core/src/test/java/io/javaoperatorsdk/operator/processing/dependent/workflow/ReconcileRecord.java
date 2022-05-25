package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class ReconcileRecord {

  private DependentResource<?, ?> dependentResource;
  private final boolean deleted;

  public ReconcileRecord(DependentResource<?, ?> dependentResource) {
    this(dependentResource, false);
  }

  public ReconcileRecord(DependentResource<?, ?> dependentResource, boolean deleted) {
    this.dependentResource = dependentResource;
    this.deleted = deleted;
  }

  public DependentResource<?, ?> getDependentResource() {
    return dependentResource;
  }

  public boolean isDeleted() {
    return deleted;
  }
}
