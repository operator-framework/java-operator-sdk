package io.javaoperatorsdk.operator.config.runtime;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.test.appender.ListAppender;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;
import io.javaoperatorsdk.operator.ControllerUtils;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.Controller;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class DefaultConfigurationServiceTest {

  public static final String CUSTOM_FINALIZER_NAME = "a.custom/finalizer";

  @Test
  void attemptingToRetrieveAnUnknownControllerShouldLogWarning() {
    final var configurationService = DefaultConfigurationService.instance();

    final LoggerContext context = LoggerContext.getContext(false);
    final PatternLayout layout = PatternLayout.createDefaultLayout(context.getConfiguration());
    final ListAppender appender = new ListAppender("list", null, layout, false, false);

    appender.start();

    context.getConfiguration().addAppender(appender);

    AppenderRef ref = AppenderRef.createAppenderRef("list", null, null);
    final var loggerName = configurationService.getLoggerName();
    LoggerConfig loggerConfig =
        LoggerConfig.createLogger(
            false,
            Level.valueOf("info"),
            loggerName,
            "false",
            new AppenderRef[] {ref},
            null,
            context.getConfiguration(),
            null);
    loggerConfig.addAppender(appender, null, null);

    context.getConfiguration().addLogger(loggerName, loggerConfig);
    context.updateLoggers();

    try {
      final var config =
          configurationService
              .getConfigurationFor(new NotAutomaticallyCreated(), false);

      assertThat(config).isNull();
      assertThat(appender.getMessages())
          .hasSize(1)
          .allMatch(m -> m.contains(NotAutomaticallyCreated.NAME) && m.contains("not found"));
    } finally {
      appender.stop();

      context.getConfiguration().removeLogger(loggerName);
      context.updateLoggers();
    }
  }

  @Test
  public void returnsValuesFromControllerAnnotationFinalizer() {
    final var controller = new TestCustomResourceController();
    final var configuration =
        DefaultConfigurationService.instance().getConfigurationFor(controller);
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
        DefaultConfigurationService.instance().getConfigurationFor(controller);
    assertEquals(CUSTOM_FINALIZER_NAME, configuration.getFinalizer());
  }

  @Test
  public void supportsInnerClassCustomResources() {
    final var controller = new TestCustomFinalizerController();
    assertDoesNotThrow(
        () -> {
          DefaultConfigurationService.instance()
              .getConfigurationFor(controller)
              .getAssociatedControllerClassName();
        });
  }

  @Controller(finalizerName = CUSTOM_FINALIZER_NAME)
  static class TestCustomFinalizerController
      implements ResourceController<TestCustomFinalizerController.InnerCustomResource> {

    @Override
    public UpdateControl<TestCustomFinalizerController.InnerCustomResource> createOrUpdateResource(
        InnerCustomResource resource, Context context) {
      return null;
    }

    @Group("test.crd")
    @Version("v1")
    public static class InnerCustomResource extends CustomResource<Void, Void> {
    }
  }

  @Controller(name = NotAutomaticallyCreated.NAME)
  static class NotAutomaticallyCreated implements ResourceController<TestCustomResource> {

    public static final String NAME = "should-be-logged";

    @Override
    public UpdateControl<TestCustomResource> createOrUpdateResource(
        TestCustomResource resource, Context context) {
      return null;
    }
  }

  @Controller(generationAwareEventProcessing = false, name = "test")
  static class TestCustomResourceController implements ResourceController<TestCustomResource> {

    @Override
    public UpdateControl<TestCustomResource> createOrUpdateResource(
        TestCustomResource resource, Context context) {
      return null;
    }
  }
}
