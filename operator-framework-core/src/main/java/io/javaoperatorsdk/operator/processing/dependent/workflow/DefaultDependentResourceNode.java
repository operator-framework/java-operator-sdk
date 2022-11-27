package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

public class DefaultDependentResourceNode<R, P extends HasMetadata>
    extends AbstractDependentResourceNode<R, P> {

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
    setDependentResource(dependentResource);
    setReconcilePrecondition(reconcilePrecondition);
    setDeletePostcondition(deletePostcondition);
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" + getDependentResource() + '}';
  }
}
