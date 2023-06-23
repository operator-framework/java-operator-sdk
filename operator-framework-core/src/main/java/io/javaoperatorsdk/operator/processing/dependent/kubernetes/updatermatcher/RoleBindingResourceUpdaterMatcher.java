package io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class RoleBindingResourceUpdaterMatcher
    extends GenericResourceUpdaterMatcher<RoleBinding> {

  @Override
  protected void updateClonedActual(RoleBinding actual, RoleBinding desired) {
    actual.setRoleRef(desired.getRoleRef());
    actual.setSubjects(desired.getSubjects());
  }

  @Override
  public boolean matches(RoleBinding actual, RoleBinding desired,
      Context<?> context) {
    return Objects.equals(actual.getRoleRef(), desired.getRoleRef()) &&
        Objects.equals(actual.getSubjects(), desired.getSubjects());
  }
}
