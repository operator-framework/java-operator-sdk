package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;

public class RoleBindingResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<RoleBinding> {

  @Override
  protected void updateClonedActual(RoleBinding actual, RoleBinding desired) {
    actual.setRoleRef(desired.getRoleRef());
    actual.setSubjects(desired.getSubjects());
  }

  @Override
  public boolean matches(RoleBinding actual, RoleBinding desired, boolean equality) {
    return Objects.equals(actual.getRoleRef(), desired.getRoleRef()) &&
        Objects.equals(actual.getSubjects(), desired.getSubjects());
  }
}
