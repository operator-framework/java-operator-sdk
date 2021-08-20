package io.javaoperatorsdk.operator.config.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.api.config.DefaultConfigurationService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

public class DefaultConfigurationServiceTest {

  public static final String CUSTOM_FINALIZER_NAME = "a.custom/finalizer";

  @Test
  void attemptingToRetrieveAnUnknownControllerShouldLogWarning() {
    final var logger = (Logger) LoggerFactory.getLogger(DefaultConfigurationService.LOGGER_NAME);
    final var appender = new ListAppender<ILoggingEvent>();
    logger.addAppender(appender);
    appender.start();
    try {
      final var config =
          io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService.instance()
              .getConfigurationFor(new NotAutomaticallyCreated(), false);
      assertNull(config);
      assertEquals(1, appender.list.size());
      assertTrue(
          appender.list.stream()
              .allMatch(
                  e -> {
                    final var message = e.getFormattedMessage();
                    return message.contains(NotAutomaticallyCreated.NAME)
                        && message.contains("not found");
                  }));
    } finally {
      logger.detachAndStopAllAppenders();
    }
  }

  @Test
  public void returnsValuesFromControllerAnnotationFinalizer() {
    final var controller = new TestCustomResourceController();
    final var configuration =
        io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService.instance()
            .getConfigurationFor(controller);
    assertEquals(CustomResource.getCRDName(TestCustomResource.class), configuration.getCRDName());
    assertEquals(
        ControllerUtils.getDefaultFinalizerName(configuration.getCRDName()),
        configuration.getFinalizer());
    assertEquals(TestCustomResource.class, configuration.getCustomResourceClass());
    assertFalse(configuration.isGenerationAware());
  }

  @Test
  public void returnCustomerFinalizerNameIfSet() {
    final var controller = new TestCustomFinalizerController();
    final var configuration =
        io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService.instance()
            .getConfigurationFor(controller);
    assertEquals(CUSTOM_FINALIZER_NAME, configuration.getFinalizer());
  }

  @Test
  public void supportsInnerClassCustomResources() {
    final var controller = new TestCustomFinalizerController();
    assertDoesNotThrow(
        () -> {
          io.javaoperatorsdk.operator.config.runtime.DefaultConfigurationService.instance()
              .getConfigurationFor(controller)
              .getAssociatedControllerClassName();
        });
  }

  @Controller(finalizerName = CUSTOM_FINALIZER_NAME)
  static class TestCustomFinalizerController
      implements ResourceController<TestCustomFinalizerController.InnerCustomResource> {

    @Group("test.crd")
    @Version("v1")
    public class InnerCustomResource extends CustomResource {}

    @Override
    public UpdateControl<TestCustomFinalizerController.InnerCustomResource> createOrUpdateResource(
        InnerCustomResource resource, Context<InnerCustomResource> context) {
      return null;
    }
  }

  @Controller(name = NotAutomaticallyCreated.NAME)
  static class NotAutomaticallyCreated implements ResourceController<TestCustomResource> {

    public static final String NAME = "should-be-logged";

    @Override
    public UpdateControl<TestCustomResource> createOrUpdateResource(
        TestCustomResource resource, Context<TestCustomResource> context) {
      return null;
    }
  }

  @Controller(generationAwareEventProcessing = false, name = "test")
  static class TestCustomResourceController implements ResourceController<TestCustomResource> {

    @Override
    public UpdateControl<TestCustomResource> createOrUpdateResource(
        TestCustomResource resource, Context<TestCustomResource> context) {
      return null;
    }
  }
}
