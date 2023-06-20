package io.javaoperatorsdk.operator.processing.dependent.kubernetes.processors;

import java.util.Objects;

import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.javaoperatorsdk.operator.api.reconciler.Context;

public class ClusterRoleBindingResourceUpdatePreProcessor
    extends GenericResourceUpdatePreProcessor<ClusterRoleBinding> {

  @Override
  protected void updateClonedActual(ClusterRoleBinding actual, ClusterRoleBinding desired) {
    actual.setRoleRef(desired.getRoleRef());
    actual.setSubjects(desired.getSubjects());
  }

  @Override
  public boolean matches(ClusterRoleBinding actual, ClusterRoleBinding desired, boolean equality,
      Context<?> context, String[] ignoredPaths) {
    return Objects.equals(actual.getRoleRef(), desired.getRoleRef()) &&
        Objects.equals(actual.getSubjects(), desired.getSubjects());
  }
}
