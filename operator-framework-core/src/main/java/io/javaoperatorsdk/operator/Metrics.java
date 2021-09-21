package io.javaoperatorsdk.operator;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopDistributionSummary;
import io.micrometer.core.instrument.noop.NoopFunctionCounter;
import io.micrometer.core.instrument.noop.NoopFunctionTimer;
import io.micrometer.core.instrument.noop.NoopGauge;
import io.micrometer.core.instrument.noop.NoopMeter;
import io.micrometer.core.instrument.noop.NoopTimer;

public class Metrics {
  public static final Metrics NOOP = new Metrics(new NoopMeterRegistry(Clock.SYSTEM));
  public static final String PREFIX = "operator.sdk.";
  private final MeterRegistry registry;

  public Metrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public interface ControllerExecution<T> {
    String name();

    String controllerName();

    String successTypeName(T result);

    T execute();
  }

  public <T> T timeControllerExecution(ControllerExecution<T> execution) {
    final var name = execution.controllerName();
    final var execName = PREFIX + "controllers.execution." + execution.name();
    final var timer =
        Timer.builder(execName)
            .tags("controller", name)
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(registry);
    try {
      final var result = timer.record(execution::execute);
      final var successType = execution.successTypeName(result);
      registry
          .counter(execName + ".success", "controller", name, "type", successType)
          .increment();
      return result;
    } catch (Exception e) {
      final var exception = e.getClass().getSimpleName();
      registry
          .counter(execName + ".failure", "controller", name, "exception", exception)
          .increment();
      throw e;
    }
  }

  public void incrementControllerRetriesNumber() {
    registry
        .counter(
            PREFIX + "retry.on.exception", "retry", "retryCounter", "type",
            "retryException")
        .increment();

  }

  public void incrementProcessedEventsNumber() {
    registry
        .counter(
            PREFIX + "total.events.received", "events", "totalEvents", "type",
            "eventsReceived")
        .increment();

  }

  public <T extends Map<?, ?>> T monitorSizeOf(T map, String name) {
    return registry.gaugeMapSize(PREFIX + name + ".size", Collections.emptyList(), map);
  }

  public static class NoopMeterRegistry extends MeterRegistry {
    public NoopMeterRegistry(Clock clock) {
      super(clock);
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T t, ToDoubleFunction<T> toDoubleFunction) {
      return new NoopGauge(id);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
      return new NoopCounter(id);
    }

    @Override
    protected Timer newTimer(
        Meter.Id id,
        DistributionStatisticConfig distributionStatisticConfig,
        PauseDetector pauseDetector) {
      return new NoopTimer(id);
    }

    @Override
    protected DistributionSummary newDistributionSummary(
        Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double v) {
      return new NoopDistributionSummary(id);
    }

    @Override
    protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> iterable) {
      return new NoopMeter(id);
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(
        Meter.Id id,
        T t,
        ToLongFunction<T> toLongFunction,
        ToDoubleFunction<T> toDoubleFunction,
        TimeUnit timeUnit) {
      return new NoopFunctionTimer(id);
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(
        Meter.Id id, T t, ToDoubleFunction<T> toDoubleFunction) {
      return new NoopFunctionCounter(id);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
      return TimeUnit.SECONDS;
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
      return DistributionStatisticConfig.NONE;
    }
  }
}
