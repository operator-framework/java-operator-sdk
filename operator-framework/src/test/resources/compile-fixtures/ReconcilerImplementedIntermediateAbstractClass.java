package io;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.UpdateControl;
import java.io.Serializable;

@Controller
public class ReconcilerImplementedIntermediateAbstractClass extends
    AbstractReconciler<AbstractReconciler.MyCustomResource> implements Serializable {

  public UpdateControl<AbstractReconciler.MyCustomResource> createOrUpdateResources(
      AbstractReconciler.MyCustomResource customResource,
      Context context) {
    return UpdateControl.updateCustomResource(null);
  }

  public DeleteControl deleteResources(AbstractReconciler.MyCustomResource customResource,
      Context context) {
    return DeleteControl.defaultDelete();
  }
}
