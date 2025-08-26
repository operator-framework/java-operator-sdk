package io.javaoperatorsdk.operator.monitoring.micrometer;

import java.util.Collections;
import java.util.Set;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultBehaviorIT extends AbstractMicrometerMetricsTestFixture {

  @Override
  protected MicrometerMetrics getMetrics() {
    return MicrometerMetrics.newMicrometerMetricsBuilder(registry).build();
  }

  @Override
  protected Set<Meter.Id> preDeleteChecks(ResourceID resourceID) {
    // no meter should be recorded because we're not tracking anything to be deleted later
    assertThat(metrics.recordedMeterIdsFor(resourceID)).isEmpty();
    // metrics are collected per resource by default for now, this will change in a future release
    assertThat(registry.getMetersAsString()).contains(resourceID.getName());
    return Collections.emptySet();
  }

  @Override
  protected void postDeleteChecks(ResourceID resourceID, Set<Meter.Id> recordedMeters) {
    // meters should be neither recorded, nor removed by default
    assertThat(registry.getRemoved()).isEmpty();
  }
}
