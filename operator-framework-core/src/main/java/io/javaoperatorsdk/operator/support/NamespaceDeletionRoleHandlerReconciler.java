package io.javaoperatorsdk.operator.support;

import io.fabric8.kubernetes.api.model.rbac.Role;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class NamespaceDeletionRoleHandlerReconciler implements Reconciler<Role> {

  @Override
  public UpdateControl<Role> reconcile(Role resource, Context<Role> context) throws Exception {


    return UpdateControl.noUpdate();
  }
}
