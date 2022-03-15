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
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultConfigurationServiceTest {

  public static final String CUSTOM_FINALIZER_NAME = "a.custom/finalizer";
  final DefaultConfigurationService configurationService = DefaultConfigurationService.instance();

  @Test
  void attemptingToRetrieveAnUnknownControllerShouldLogWarning() {
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
  void returnsValuesFromControllerAnnotationFinalizer() {
    final var reconciler = new TestCustomReconciler();
    final var configuration = configurationService.getConfigurationFor(reconciler);
    assertEquals(CustomResource.getCRDName(TestCustomResource.class),
        configuration.getResourceTypeName());
    assertEquals(
        ReconcilerUtils.getDefaultFinalizerName(TestCustomResource.class),
        configuration.getFinalizerName());
    assertEquals(TestCustomResource.class, configuration.getResourceClass());
    assertFalse(configuration.isGenerationAware());
  }

  @Test
  void returnCustomerFinalizerNameIfSet() {
    final var reconciler = new TestCustomFinalizerReconciler();
    final var configuration = configurationService.getConfigurationFor(reconciler);
    assertEquals(CUSTOM_FINALIZER_NAME, configuration.getFinalizerName());
  }

  @Test
  void supportsInnerClassCustomResources() {
    final var reconciler = new TestCustomFinalizerReconciler();
    assertDoesNotThrow(
        () -> {
          configurationService.getConfigurationFor(reconciler).getAssociatedReconcilerClassName();
        });
  }

  @ControllerConfiguration(finalizerName = CUSTOM_FINALIZER_NAME)
  static class TestCustomFinalizerReconciler
      implements Reconciler<TestCustomFinalizerReconciler.InnerCustomResource> {

    @Override
    public UpdateControl<TestCustomFinalizerReconciler.InnerCustomResource> reconcile(
        InnerCustomResource resource, Context<InnerCustomResource> context) {
      return null;
    }

    @Group("test.crd")
    @Version("v1")
    public static class InnerCustomResource extends CustomResource<Void, Void> {
    }
  }

  @ControllerConfiguration(name = NotAutomaticallyCreated.NAME)
  static class NotAutomaticallyCreated implements Reconciler<TestCustomResource> {

    public static final String NAME = "should-be-logged";

    @Override
    public UpdateControl<TestCustomResource> reconcile(
        TestCustomResource resource, Context<TestCustomResource> context) {
      return null;
    }
  }

  @ControllerConfiguration(generationAwareEventProcessing = false, name = "test")
  static class TestCustomReconciler implements Reconciler<TestCustomResource> {

    @Override
    public UpdateControl<TestCustomResource> reconcile(
        TestCustomResource resource, Context<TestCustomResource> context) {
      return null;
    }
  }
}
