package io.javaoperatorsdk.operator.monitoring.micrometer;

import io.micrometer.core.instrument.MeterRegistry;

public class DelayedMetricsCleaningOnDeleteWithCustomImplementationIT
    extends DelayedMetricsCleaningOnDeleteIT {

  @Override
  protected MicrometerMetrics getMetrics() {
    return new TestMetrics(registry);
  }

  private static class TestMetrics extends MicrometerMetrics {

    private TestMetrics(MeterRegistry registry) {
      super(
          registry,
          new CleanerBuilder(registry)
              .withCleanUpDelayInSeconds(testDelay)
              .withCleaningThreadNumber(2)
              .build(),
          true);
    }
  }
}
