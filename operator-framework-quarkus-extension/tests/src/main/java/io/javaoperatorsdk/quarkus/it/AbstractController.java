package io.javaoperatorsdk.quarkus.it;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

public abstract class AbstractController<T extends TestResource> implements ResourceController<T> {

  @Override
  public DeleteControl deleteResource(T resource, Context<T> context) {
    return null;
  }

  @Override
  public UpdateControl<T> createOrUpdateResource(T resource, Context<T> context) {
    return null;
  }
}
