package io.javaoperatorsdk.operator.springboot.starter;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import org.springframework.stereotype.Component;

@Component
@Controller
public class TestController implements ResourceController {

  @Override
  public DeleteControl deleteResource(CustomResource resource, Context context) {
    return DeleteControl.DEFAULT_DELETE;
  }

  @Override
  public UpdateControl createOrUpdateResource(CustomResource resource, Context context) {
    return UpdateControl.noUpdate();
  }
}
