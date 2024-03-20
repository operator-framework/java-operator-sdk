package io.javaoperatorsdk.operator.monitoring.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.micrometer.core.instrument.Meter;
import java.util.Collections;
import java.util.Set;

public class NoPerResourceCollectionIT extends AbstractMicrometerMetricsTestFixture {
  @Override
  protected MicrometerMetrics getMetrics() {
    return MicrometerMetrics.withoutPerResourceMetrics(registry);
  }

  @Override
  protected Set<Meter.Id> preDeleteChecks(ResourceID resourceID) {
    assertThat(metrics.recordedMeterIdsFor(resourceID)).isEmpty();
    assertThat(registry.getMetersAsString()).doesNotContain(resourceID.getName());
    return Collections.emptySet();
  }
}
