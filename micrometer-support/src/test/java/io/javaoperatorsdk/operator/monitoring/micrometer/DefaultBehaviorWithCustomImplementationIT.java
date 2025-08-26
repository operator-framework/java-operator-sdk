package io.javaoperatorsdk.operator.monitoring.micrometer;

import io.micrometer.core.instrument.MeterRegistry;

public class DefaultBehaviorWithCustomImplementationIT extends DefaultBehaviorIT {

  @Override
  protected MicrometerMetrics getMetrics() {
    return new TestMetrics(registry);
  }

  private static class TestMetrics extends MicrometerMetrics {

    private TestMetrics(MeterRegistry registry) {
      super(registry, new CleanerBuilder(registry).build(), true);
    }
  }
}
