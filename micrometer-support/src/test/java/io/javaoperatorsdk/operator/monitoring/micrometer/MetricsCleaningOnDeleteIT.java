package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceProvider;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.MeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class MetricsCleaningOnDeleteIT {
  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new MetricsCleaningTestReconciler())
          .build();

  private static final MeterRegistry mockRegistry = mock(MeterRegistry.class);
  private static final int testDelay = 15;
  private static final TestMetrics metrics = new TestMetrics(mockRegistry, testDelay);
  private static final String testResourceName = "cleaning-metrics-cr";

  @BeforeAll
  static void setup() {
    ConfigurationServiceProvider.overrideCurrent(
        overrider -> overrider.withMetrics(new TestMetrics(mockRegistry, testDelay)));
  }

  @AfterAll
  static void reset() {
    ConfigurationServiceProvider.reset();
  }

  @Test
  void addsFinalizerAndCallsCleanupIfCleanerImplemented() {
    var testResource = new ConfigMapBuilder()
        .withNewMetadata()
        .withName(testResourceName)
        .endMetadata()
        .build();
    final var created = operator.create(testResource);

    var meters = metrics.recordedMeterIdsFor(ResourceID.fromResource(created));
    assertThat(meters).isNotNull();
    assertThat(meters).isNotEmpty();

    await().until(() -> !operator.get(ConfigMap.class, testResourceName)
        .getMetadata().getFinalizers().isEmpty());

    operator.delete(testResource);

    await().until(() -> operator.get(ConfigMap.class, testResourceName) == null);

    await().atLeast(testDelay + 3, TimeUnit.SECONDS);
    meters.forEach(id -> verify(mockRegistry.remove(id)));
    meters = metrics.recordedMeterIdsFor(ResourceID.fromResource(created));
    assertThat(meters).isNull();
  }

  @ControllerConfiguration
  private static class MetricsCleaningTestReconciler
      implements Reconciler<ConfigMap>, Cleaner<ConfigMap> {
    @Override
    public UpdateControl<ConfigMap> reconcile(ConfigMap resource, Context<ConfigMap> context) {
      return UpdateControl.noUpdate();
    }

    @Override
    public DeleteControl cleanup(ConfigMap resource, Context<ConfigMap> context) {
      return DeleteControl.defaultDelete();
    }
  }

  private static class TestMetrics extends MicrometerMetrics {

    public TestMetrics(MeterRegistry registry, int cleanUpDelayInSeconds) {
      super(registry, cleanUpDelayInSeconds, 2);
    }
  }
}
