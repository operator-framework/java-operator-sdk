package io.javaoperatorsdk.quarkus.extension.deployment;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

public class TestController implements ResourceController<TestResource> {

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
