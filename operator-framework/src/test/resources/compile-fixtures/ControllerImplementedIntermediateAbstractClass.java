package io;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.UpdateControl;
import java.io.Serializable;

@Controller
public class ControllerImplementedIntermediateAbstractClass extends
    AbstractController<AbstractController.MyCustomResource> implements Serializable {

  public UpdateControl<AbstractController.MyCustomResource> createOrUpdateResource(
      AbstractController.MyCustomResource customResource,
      Context<AbstractController.MyCustomResource> context) {
    return UpdateControl.updateCustomResource(null);
  }

  public DeleteControl deleteResource(AbstractController.MyCustomResource customResource,
      Context<AbstractController.MyCustomResource> context) {
    return DeleteControl.defaultDelete();
  }
}
