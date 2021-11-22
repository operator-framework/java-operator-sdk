package io;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.io.Serializable;

@ControllerConfiguration
public class ReconcilerImplementedIntermediateAbstractClass extends
    AbstractReconciler<AbstractReconciler.MyCustomResource> implements Serializable {

  public UpdateControl<AbstractReconciler.MyCustomResource> reconcile(
      AbstractReconciler.MyCustomResource customResource,
      Context context) {
    return UpdateControl.updateResource(null);
  }

  public DeleteControl cleanup(AbstractReconciler.MyCustomResource customResource,
      Context context) {
    return DeleteControl.defaultDelete();
  }
}
