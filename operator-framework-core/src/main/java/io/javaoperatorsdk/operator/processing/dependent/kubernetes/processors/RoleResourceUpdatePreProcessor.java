package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import io.fabric8.kubernetes.api.model.rbac.Role;

public class RoleResourceUpdatePreProcessor extends GenericResourceUpdatePreProcessor<Role> {

  @Override
  protected void updateClonedActual(Role actual, Role desired) {
    actual.setRules(desired.getRules());
  }
}
