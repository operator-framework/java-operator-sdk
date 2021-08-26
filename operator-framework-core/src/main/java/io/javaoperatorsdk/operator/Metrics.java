package io.javaoperatorsdk.operator;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.DeleteControl;
import io.javaoperatorsdk.operator.api.ResourceController;
import io.javaoperatorsdk.operator.api.UpdateControl;
import io.javaoperatorsdk.operator.api.config.ControllerConfiguration;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.noop.*;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public class Metrics {
  public static final Metrics NOOP = new Metrics(new NoopMeterRegistry(Clock.SYSTEM));
  private final MeterRegistry registry;

  public Metrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public <R extends CustomResource> UpdateControl<R> timeControllerCreateOrUpdate(
      ResourceController<R> controller,
      ControllerConfiguration<R> configuration,
      R resource,
      Context<R> context) {
    final var name = configuration.getName();
    final var timer =
        Timer.builder("operator.sdk.controllers.execution.createorupdate")
            .tags("controller", name)
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(registry);
    try {
      final var result = timer.record(() -> controller.createOrUpdateResource(resource, context));
      System.out.println("I am here in create update");
      String successType = "cr";
      if (result.isUpdateStatusSubResource()) {
        successType = "status";
      }
      if (result.isUpdateCustomResourceAndStatusSubResource()) {
        successType = "both";
      }
      registry
          .counter(
              "operator.sdk.controllers.execution.success", "controller", name, "type", successType)
          .increment();
      return result;
    } catch (Exception e) {
      registry
          .counter(
              "operator.sdk.controllers.execution.failure",
              "controller",
              name,
              "exception",
              e.getClass().getSimpleName())
          .increment();
      throw e;
    }
  }

  public DeleteControl timeControllerDelete(
      ResourceController controller,
      ControllerConfiguration configuration,
      CustomResource resource,
      Context context) {
    final var name = configuration.getName();
    final var timer =
        Timer.builder("operator.sdk.controllers.execution.delete")
            .tags("controller", name)
            .publishPercentiles(0.3, 0.5, 0.95)
            .publishPercentileHistogram()
            .register(registry);
    try {
      final var result = timer.record(() -> controller.deleteResource(resource, context));
      System.out.println("I am here in delete");
      String successType = "notDelete";
      if (result == DeleteControl.DEFAULT_DELETE) {
        successType = "delete";
      }
      registry
          .counter(
              "operator.sdk.controllers.execution.success", "controller", name, "type", successType)
          .increment();
      return result;
    } catch (Exception e) {
      registry
          .counter(
              "operator.sdk.controllers.execution.failure",
              "controller",
              name,
              "exception",
              e.getClass().getSimpleName())
          .increment();
      throw e;
    }
  }

  public void timeControllerRetry() {

    registry
        .counter(
            "operator.sdk.retry.on.exception", "retry", "retryCounter", "type",
            "retryException")
        .increment();

  }

  public void timeControllerEvents() {

    registry
        .counter(
            "operator.sdk.total.events.received", "events", "totalEvents", "type",
            "eventsReceived")
        .increment();

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
