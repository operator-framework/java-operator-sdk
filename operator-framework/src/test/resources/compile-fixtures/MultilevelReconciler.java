package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class MultilevelReconciler extends
    MultilevelAbstractReconciler<String, MultilevelReconciler.MyCustomResource> {

  public static class MyCustomResource extends CustomResource<Void,Void> {

  }

  public UpdateControl<MultilevelReconciler.MyCustomResource> reconcile(
          MultilevelReconciler.MyCustomResource customResource,
      Context context) {
    return UpdateControl.updateResource(null);
  }

  public DeleteControl cleanup(MultilevelReconciler.MyCustomResource customResource,
      Context context) {
    return DeleteControl.defaultDelete();
  }

}
