package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;

public class RoleBindingResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<RoleBinding> {

  @Override
  protected void updateClonedActual(RoleBinding actual, RoleBinding desired) {
    actual.setRoleRef(desired.getRoleRef());
    actual.setSubjects(desired.getSubjects());
  }
}
