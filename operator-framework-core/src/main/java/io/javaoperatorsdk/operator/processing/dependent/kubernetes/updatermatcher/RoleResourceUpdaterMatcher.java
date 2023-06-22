package io.javaoperatorsdk.operator.processing.dependent.kubernetes.updatermatcher;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.rbac.Role;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class RoleResourceUpdaterMatcher extends GenericResourceUpdaterMatcher<Role> {

  @Override
  protected void updateClonedActual(Role actual, Role desired) {
    actual.setRules(desired.getRules());
  }

  @Override
  public boolean matches(Role actual, Role desired, Context<?> context) {
    return Objects.equals(actual.getRules(), desired.getRules());
  }
}
