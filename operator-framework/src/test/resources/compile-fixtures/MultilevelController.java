package io;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.UpdateControl;

@Controller
public class MultilevelController extends
    MultilevelAbstractController<String, MultilevelController.MyCustomResource> {

  public static class MyCustomResource extends CustomResource {

  }

  public UpdateControl<MultilevelController.MyCustomResource> createOrUpdateResource(
      MultilevelController.MyCustomResource customResource,
      Context<MultilevelController.MyCustomResource> context) {
    return UpdateControl.updateCustomResource(null);
  }

  public DeleteControl deleteResource(MultilevelController.MyCustomResource customResource,
      Context<MultilevelController.MyCustomResource> context) {
    return DeleteControl.defaultDelete();
  }

}
