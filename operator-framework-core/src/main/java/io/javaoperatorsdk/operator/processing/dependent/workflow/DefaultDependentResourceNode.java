package io.javaoperatorsdk.operator.processing.dependent.workflow;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;

class DefaultDependentResourceNode<R, P extends HasMetadata>
    extends AbstractDependentResourceNode<R, P> {

  public DefaultDependentResourceNode(DependentResource<R, P> dependentResource) {
    this(dependentResource, null, null);
  }

  public DefaultDependentResourceNode(DependentResource<R, P> dependentResource,
      Condition<R, P> reconcilePrecondition, Condition<R, P> deletePostcondition) {
    super(getNameFor(dependentResource));
    setDependentResource(dependentResource);
    setReconcilePrecondition(reconcilePrecondition);
    setDeletePostcondition(deletePostcondition);
  }

  @SuppressWarnings("rawtypes")
  static String getNameFor(DependentResource dependentResource) {
    return DependentResource.defaultNameFor(dependentResource.getClass()) + "#"
        + dependentResource.hashCode();
  }

  @Override
  public String toString() {
    return "DependentResourceNode{" + getDependentResource() + '}';
  }
}
