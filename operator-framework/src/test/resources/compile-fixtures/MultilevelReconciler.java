package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Controller;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@Controller
public class MultilevelReconciler extends
    MultilevelAbstractReconciler<String, MultilevelReconciler.MyCustomResource> {

  public static class MyCustomResource extends CustomResource<Void,Void> {

  }

  public UpdateControl<MultilevelReconciler.MyCustomResource> createOrUpdateResources(
          MultilevelReconciler.MyCustomResource customResource,
      Context context) {
    return UpdateControl.updateCustomResource(null);
  }

  public DeleteControl deleteResources(MultilevelReconciler.MyCustomResource customResource,
      Context context) {
    return DeleteControl.defaultDelete();
  }

}
