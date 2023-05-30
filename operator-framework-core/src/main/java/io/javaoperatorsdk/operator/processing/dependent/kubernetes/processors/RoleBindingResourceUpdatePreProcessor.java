package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RoleBindingResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<RoleBinding> {

  @Override
  protected void updateClonedActual(RoleBinding actual, RoleBinding desired) {
    actual.setRoleRef(desired.getRoleRef());
    actual.setSubjects(desired.getSubjects());
  }

  @Override
  public boolean matches(RoleBinding actual, RoleBinding desired, boolean equality,
      ObjectMapper objectMapper,
      String[] ignoredPaths) {
    return Objects.equals(actual.getRoleRef(), desired.getRoleRef()) &&
        Objects.equals(actual.getSubjects(), desired.getSubjects());
  }
}
