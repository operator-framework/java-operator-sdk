package io.javaoperatorsdk.operator.monitoring.micrometer;

import org.junit.jupiter.api.Test;

import io.javaoperatorsdk.operator.monitoring.micrometer.MicrometerMetrics.CleanerBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

class CleanerBuilderTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Test
  void test_defaultsToNoopCleaner() {
    final var cleaner = new CleanerBuilder(registry).build();
    assertThat(cleaner).isSameAs(MicrometerMetrics.Cleaner.NOOP);
  }

  @Test
  void test_withCleaningThreadNumber() {
    final var cleaner = new CleanerBuilder(registry).withCleaningThreadNumber(42).build();
    assertThat(cleaner).isInstanceOf(MicrometerMetrics.DelayedCleaner.class);
    if (cleaner instanceof MicrometerMetrics.DelayedCleaner delayedCleaner) {
      assertThat(delayedCleaner.getCleanUpDelayInSeconds()).isEqualTo(0);
    }
  }

  @Test
  void test_withCleaningThreadNumber_whenNegative_thenApplyDefault() {
    final var cleaner = new CleanerBuilder(registry).withCleaningThreadNumber(42).build();
    assertThat(cleaner).isInstanceOf(MicrometerMetrics.DelayedCleaner.class);
    if (cleaner instanceof MicrometerMetrics.DelayedCleaner delayedCleaner) {
      assertThat(delayedCleaner.getCleanUpDelayInSeconds()).isEqualTo(0);
    }
  }

  @Test
  void test_withCleaningThreadNumber_whenZero_thenApplyDefault() {
    final var cleaner = new CleanerBuilder(registry).withCleaningThreadNumber(0).build();
    assertThat(cleaner).isInstanceOf(MicrometerMetrics.DelayedCleaner.class);
    if (cleaner instanceof MicrometerMetrics.DelayedCleaner delayedCleaner) {
      assertThat(delayedCleaner.getCleanUpDelayInSeconds()).isEqualTo(0);
    }
  }

  @Test
  void test_withCleanUpDelayInSeconds() {
    final var cleaner = new CleanerBuilder(registry).withCleanUpDelayInSeconds(23).build();
    assertThat(cleaner).isInstanceOf(MicrometerMetrics.DelayedCleaner.class);
    if (cleaner instanceof MicrometerMetrics.DelayedCleaner delayedCleaner) {
      assertThat(delayedCleaner.getCleanUpDelayInSeconds()).isEqualTo(23);
    }
  }

  @Test
  void test_withCleanUpDelayInSeconds_whenNegative_thenApplyDefault() {
    final var cleaner = new CleanerBuilder(registry).withCleanUpDelayInSeconds(-23).build();
    assertThat(cleaner).isInstanceOf(MicrometerMetrics.DelayedCleaner.class);
    if (cleaner instanceof MicrometerMetrics.DelayedCleaner delayedCleaner) {
      assertThat(delayedCleaner.getCleanUpDelayInSeconds()).isEqualTo(1);
    }
  }

  @Test
  void test_withCleanUpDelayInSeconds_whenZero_thenApplyDefault() {
    final var cleaner = new CleanerBuilder(registry).withCleanUpDelayInSeconds(0).build();
    assertThat(cleaner).isInstanceOf(MicrometerMetrics.DelayedCleaner.class);
    if (cleaner instanceof MicrometerMetrics.DelayedCleaner delayedCleaner) {
      assertThat(delayedCleaner.getCleanUpDelayInSeconds()).isEqualTo(1);
    }
  }
}
