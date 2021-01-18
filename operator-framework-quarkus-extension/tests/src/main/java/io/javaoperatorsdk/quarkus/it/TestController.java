package io.javaoperatorsdk.quarkus.it;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

@Controller(name = TestController.NAME)
public class TestController implements ResourceController<TestResource> {
  public static final String NAME = "test";

  @Override
  public DeleteControl deleteResource(TestResource resource, Context<TestResource> context) {
    return null;
  }

  @Override
  public UpdateControl<TestResource> createOrUpdateResource(
      TestResource resource, Context<TestResource> context) {
    return null;
  }
}
