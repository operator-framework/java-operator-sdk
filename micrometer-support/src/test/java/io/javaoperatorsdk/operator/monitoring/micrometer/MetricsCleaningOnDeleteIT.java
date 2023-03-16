package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.time.Duration;
import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;

import static org.assertj.core.api.Assertions.assertThat;

public class MetricsCleaningOnDeleteIT extends AbstractMicrometerMetricsTestFixture {

  private static final int testDelay = 1;

  @Override
  protected MicrometerMetrics getMetrics() {
    return MicrometerMetrics.newMicrometerMetrics(registry)
        .withCleanUpDelayInSeconds(testDelay).withCleaningThreadNumber(2).build();
  }

  @Override
  protected Set<Meter.Id> preDeleteChecks(ResourceID resourceID) {
    // check that we properly recorded meters associated with the resource
    final var meters = metrics.recordedMeterIdsFor(resourceID);
    assertThat(meters).isNotNull();
    assertThat(meters).isNotEmpty();
    return meters;
  }

  @Override
  protected void postDeleteChecks(ResourceID resourceID, Set<Meter.Id> meters) throws Exception {
    // check that the meters are properly removed after the specified delay
    Thread.sleep(Duration.ofSeconds(testDelay).toMillis());
    assertThat(registry.getRemoved()).isEqualTo(meters);
    assertThat(metrics.recordedMeterIdsFor(resourceID)).isNull();
  }
}
