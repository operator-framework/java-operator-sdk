package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultBehaviorIT extends AbstractMicrometerMetricsTestFixture {
  @Override
  protected MicrometerMetrics getMetrics() {
    return MicrometerMetrics.newMicrometerMetrics(registry).build();
  }

  @Override
  protected void postDeleteChecks(ResourceID resourceID, Set<Meter.Id> recordedMeters)
      throws Exception {
    assertThat(metrics.recordedMeterIdsFor(resourceID)).isNotNull();
    assertThat(registry.getRemoved()).isEmpty();
  }
}
