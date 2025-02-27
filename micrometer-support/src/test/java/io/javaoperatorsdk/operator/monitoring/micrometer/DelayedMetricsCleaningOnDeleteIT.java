package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.time.Duration;
import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;

import static org.assertj.core.api.Assertions.assertThat;

public class DelayedMetricsCleaningOnDeleteIT extends AbstractMicrometerMetricsTestFixture {

  private static final int testDelay = 1;

  @Override
  protected MicrometerMetrics getMetrics() {
    return MicrometerMetrics.newPerResourceCollectingMicrometerMetricsBuilder(registry)
        .withCleanUpDelayInSeconds(testDelay)
        .withCleaningThreadNumber(2)
        .build();
  }

  @Override
  protected void postDeleteChecks(ResourceID resourceID, Set<Meter.Id> recordedMeters)
      throws Exception {
    // check that the meters are properly removed after the specified delay
    Thread.sleep(Duration.ofSeconds(testDelay).toMillis());
    assertThat(registry.getRemoved()).isEqualTo(recordedMeters);
    assertThat(metrics.recordedMeterIdsFor(resourceID)).isNull();
  }
}
