package io.javaoperatorsdk.operator.support;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.javaoperatorsdk.operator.api.reconciler.*;

// todo handle also ClusterRole bindings only if has permission
@ControllerConfiguration
public class NamespaceDeletionRoleBindingReconciler
    implements Reconciler<RoleBinding>, Cleaner<RoleBinding> {

  @Override
  public UpdateControl<RoleBinding> reconcile(RoleBinding resource, Context<RoleBinding> context)
      throws Exception {

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(RoleBinding resource, Context<RoleBinding> context) {
    return null;
  }
}
