package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;

import static org.assertj.core.api.Assertions.assertThat;

public class NoDelayMetricsCleaningOnDeleteIT extends AbstractMicrometerMetricsTestFixture {
  @Override
  protected MicrometerMetrics getMetrics() {
    return MicrometerMetrics.newPerResourceCollectingMicrometerMetrics(registry)
        .withCleanUpDelayInSeconds(0).build();
  }

  @Override
  protected Set<Meter.Id> preDeleteChecks(ResourceID resourceID) {
    assertThat(metrics.getCleaner()).isInstanceOf(MicrometerMetrics.DefaultCleaner.class);
    return super.preDeleteChecks(resourceID);
  }

  @Override
  protected void postDeleteChecks(ResourceID resourceID, Set<Meter.Id> recordedMeters)
      throws Exception {
    // check that the meters are properly immediately removed
    assertThat(registry.getRemoved()).isEqualTo(recordedMeters);
    assertThat(metrics.recordedMeterIdsFor(resourceID)).isNull();
  }


}
