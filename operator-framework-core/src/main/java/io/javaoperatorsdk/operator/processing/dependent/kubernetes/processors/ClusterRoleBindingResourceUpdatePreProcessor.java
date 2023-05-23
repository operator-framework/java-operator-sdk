package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;

public class ClusterRoleBindingResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<ClusterRoleBinding> {

  @Override
  protected void updateClonedActual(ClusterRoleBinding actual, ClusterRoleBinding desired) {
    actual.setRoleRef(desired.getRoleRef());
    actual.setSubjects(desired.getSubjects());
  }

  @Override
  public boolean matches(ClusterRoleBinding actual, ClusterRoleBinding desired, boolean equality) {
    return super.matches(actual, desired, equality);
  }
}
