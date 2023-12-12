package io.javaoperatorsdk.operator.support;

import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

public class NamespaceDeletionRoleBindingHandlerReconciler implements Reconciler<RoleBinding> {

  @Override
  public UpdateControl<RoleBinding> reconcile(RoleBinding resource, Context<RoleBinding> context)
      throws Exception {

    return UpdateControl.noUpdate();
  }
}
