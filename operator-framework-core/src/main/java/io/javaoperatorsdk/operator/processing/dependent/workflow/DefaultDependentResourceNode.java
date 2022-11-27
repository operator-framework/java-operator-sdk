package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DefaultDependentResourceNode<R, P extends HasMetadata>
    extends AbstractDependentResourceNode<R, P> {

  private final DependentResource<R, P> dependentResource;

  public DefaultDependentResourceNode(DependentResource<R, P> dependentResource) {
    this(dependentResource, null, null);
  }

  public DefaultDependentResourceNode(DependentResource<R, P> dependentResource,
      Condition<R, P> reconcilePrecondition) {
    this(dependentResource, reconcilePrecondition, null);
  }

  public DefaultDependentResourceNode(DependentResource<R, P> dependentResource,
      Condition<R, P> reconcilePrecondition, Condition<R, P> deletePostcondition) {
    super(DependentResource.defaultNameFor(dependentResource.getClass()) + "#"
        + dependentResource.hashCode());
    this.dependentResource = dependentResource;
    setReconcilePrecondition(reconcilePrecondition);
    setDeletePostcondition(deletePostcondition);
  }

  public DependentResource<R, P> getDependentResource() {
    return dependentResource;
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" + dependentResource + '}';
  }
}
