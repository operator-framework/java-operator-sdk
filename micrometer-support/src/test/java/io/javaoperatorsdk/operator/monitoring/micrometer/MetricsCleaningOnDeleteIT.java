package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

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
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class MetricsCleaningOnDeleteIT {
  @RegisterExtension
  static LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder().withReconciler(new MetricsCleaningTestReconciler())
          .build();

  private static final TestSimpleMeterRegistry registry = new TestSimpleMeterRegistry();
  private static final int testDelay = 1;
  private static final MicrometerMetrics metrics = MicrometerMetrics.newMicrometerMetrics(registry)
      .withCleanUpDelayInSeconds(testDelay).withCleaningThreadNumber(2).build();
  private static final String testResourceName = "cleaning-metrics-cr";

  @BeforeAll
  static void setup() {
    ConfigurationServiceProvider.overrideCurrent(overrider -> overrider.withMetrics(metrics));
  }

  @AfterAll
  static void reset() {
    ConfigurationServiceProvider.reset();
  }

  @Test
  void removesMetersAssociatedWithResourceAfterItsDeletion() throws InterruptedException {
    var testResource = new ConfigMapBuilder()
        .withNewMetadata()
        .withName(testResourceName)
        .endMetadata()
        .build();
    final var created = operator.create(testResource);

    // make sure the resource is created
    await().until(() -> !operator.get(ConfigMap.class, testResourceName)
        .getMetadata().getFinalizers().isEmpty());

    // check that we properly recorded meters associated with the resource
    final var meters = metrics.recordedMeterIdsFor(ResourceID.fromResource(created));
    assertThat(meters).isNotNull();
    assertThat(meters).isNotEmpty();

    // delete the resource and wait for it to be deleted
    operator.delete(testResource);
    await().until(() -> operator.get(ConfigMap.class, testResourceName) == null);

    // check that the meters are properly removed after the specified delay
    Thread.sleep(Duration.ofSeconds(testDelay).toMillis());
    assertThat(registry.removed).isEqualTo(meters);
    assertThat(metrics.recordedMeterIdsFor(ResourceID.fromResource(created))).isNull();
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

  private static class TestSimpleMeterRegistry extends SimpleMeterRegistry {
    private final Set<Meter.Id> removed = new HashSet<>();

    @Override
    public Meter remove(Meter.Id mappedId) {
      final var removed = super.remove(mappedId);
      this.removed.add(removed.getId());
      return removed;
    }
  }
}
