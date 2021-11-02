package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.UpdateControl;

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
