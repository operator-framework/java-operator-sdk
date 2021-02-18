package io.javaoperatorsdk.quarkus.it;

import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.EventSourceManager;

@Controller(
    name = TestController.NAME,
    delayRegistrationUntilEvent = TestController.RegisterEvent.class)
public class TestController implements RegistrableController<TestResource> {
  // CDI Event to trigger registration
  public static class RegisterEvent {}

  public static final String NAME = "test";
  private boolean initialised;

  @Override
  public DeleteControl deleteResource(TestResource resource, Context<TestResource> context) {
    return null;
  }

  @Override
  public UpdateControl<TestResource> createOrUpdateResource(
      TestResource resource, Context<TestResource> context) {
    return null;
  }

  @Override
  public void init(EventSourceManager eventSourceManager) {
    initialised = true;
  }

  @Override
  public boolean isInitialised() {
    return initialised;
  }
}
