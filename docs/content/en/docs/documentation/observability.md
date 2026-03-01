---
title: Observability
weight: 55
---

## Runtime Info

[RuntimeInfo](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/RuntimeInfo.java#L16-L16)
is used mainly to check the actual health of event sources. Based on this information it is easy to implement custom
liveness probes.

[stopOnInformerErrorDuringStartup](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L168-L168)
setting, where this flag usually needs to be set to false, in order to control the exact liveness properties.

See also an example implementation in the
[WebPage sample](https://github.com/java-operator-sdk/java-operator-sdk/blob/3e2e7c4c834ef1c409d636156b988125744ca911/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageOperator.java#L38-L43)

## Contextual Info for Logging with MDC

Logging is enhanced with additional contextual information using
[MDC](http://www.slf4j.org/manual.html#mdc). The following attributes are available in most
parts of reconciliation logic and during the execution of the controller:

| MDC Key                    | Value added from primary resource |
|:---------------------------|:----------------------------------| 
| `resource.apiVersion`      | `.apiVersion`                     |
| `resource.kind`            | `.kind`                           |
| `resource.name`            | `.metadata.name`                  | 
| `resource.namespace`       | `.metadata.namespace`             |
| `resource.resourceVersion` | `.metadata.resourceVersion`       |
| `resource.generation`      | `.metadata.generation`            |
| `resource.uid`             | `.metadata.uid`                   |

For more information about MDC see this [link](https://www.baeldung.com/mdc-in-log4j-2-logback).

### MDC entries during event handling

Although, usually users might not require it in their day-to-day workflow, it is worth mentioning that 
there are additional MDC entries managed for event handling. Typically, you might be interested in it
in your `SecondaryToPrimaryMapper` related logs.
For `InformerEventSource` and `ControllerEventSource` the following information is present:

| MDC Key                                        | Value from Resource from the Event               |
|:-----------------------------------------------|:-------------------------------------------------|
| `eventsource.event.resource.name`              | `.metadata.name`                                 |
| `eventsource.event.resource.uid`               | `.metadata.uid`                                  |
| `eventsource.event.resource.namespace`         | `.metadata.namespace`                            |
| `eventsource.event.resource.kind`              | resource kind                                    |
| `eventsource.event.resource.resourceVersion`   | `.metadata.resourceVersion`                      |
| `eventsource.event.action`                     | action name (e.g. `ADDED`, `UPDATED`, `DELETED`) |
| `eventsource.name`                             | name of the event source                         |

### Note on null values

If a resource doesn't provide values for one of the specified keys, the key will be omitted and not added to the MDC
context. There is, however, one notable exception: the resource's namespace, where, instead of omitting the key, we emit
the `MDCUtils.NO_NAMESPACE` value instead. This allows searching for resources without namespace (notably, clustered
resources) in the logs more easily.

### Disabling MDC support

MDC support is enabled by default. If you want to disable it, you can set the `JAVA_OPERATOR_SDK_USE_MDC` environment
variable to `false` when you start your operator.

## Metrics

JOSDK provides built-in support for metrics reporting on what is happening with your reconcilers in the form of
the `Metrics` interface which can be implemented to connect to your metrics provider of choice, JOSDK calling the
methods as it goes about reconciling resources. By default, a no-operation implementation is provided thus providing a
no-cost sane default. A [micrometer](https://micrometer.io)-based implementation is also provided.

You can use a different implementation by overriding the default one provided by the default `ConfigurationService`, as
follows:

```java
Metrics metrics; // initialize your metrics implementation
Operator operator = new Operator(client, o -> o.withMetrics(metrics));
```

### MicrometerMetricsV2 (Recommended, since 5.3.0)

[`MicrometerMetricsV2`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/micrometer-support/src/main/java/io/javaoperatorsdk/operator/monitoring/micrometer/MicrometerMetricsV2.java) is the recommended micrometer-based implementation. It is designed with low cardinality in mind:
all meters are scoped to the controller, not to individual resources. This avoids unbounded cardinality growth as
resources come and go.

The simplest way to create an instance:

```java
MeterRegistry registry; // initialize your registry implementation
Metrics metrics = MicrometerMetricsV2.newPerResourceCollectingMicrometerMetricsBuilder(registry).build();
```

Optionally, include a `namespace` tag on per-reconciliation counters (disabled by default to avoid unexpected
cardinality increases in existing deployments):

```java
Metrics metrics = MicrometerMetricsV2.newPerResourceCollectingMicrometerMetricsBuilder(registry)
        .withNamespaceAsTag()
        .build();
```

You can also supply a custom timer configuration for `reconciliations.execution.duration`:

```java
Metrics metrics = MicrometerMetricsV2.newPerResourceCollectingMicrometerMetricsBuilder(registry)
        .withExecutionTimerConfig(builder -> builder.publishPercentiles(0.5, 0.95, 0.99))
        .build();
```

#### MicrometerMetricsV2 metrics

All meters use `controller.name` as their primary tag. Counters optionally carry a `namespace` tag when
`withNamespaceAsTag()` is enabled.

| Meter name (Micrometer)                  | Type    | Tags                              | Description                                                          |
|------------------------------------------|---------|-----------------------------------|----------------------------------------------------------------------|
| `reconciliations.executions`             | gauge   | `controller.name`                 | Number of reconciler executions currently in progress                |
| `reconciliations.active`                 | gauge   | `controller.name`                 | Number of resources currently queued for reconciliation              |
| `custom_resources`                       | gauge   | `controller.name`                 | Number of custom resources tracked by the controller                 |
| `reconciliations.execution.duration`     | timer   | `controller.name`                 | Reconciliation execution duration with explicit SLO bucket histogram |
| `reconciliations.started.total`          | counter | `controller.name`, `namespace`*   | Number of reconciliations started (including retries)                |
| `reconciliations.success.total`          | counter | `controller.name`, `namespace`*   | Number of successfully finished reconciliations                      |
| `reconciliations.failure.total`          | counter | `controller.name`, `namespace`*   | Number of failed reconciliations                                     |
| `reconciliations.retries.total`          | counter | `controller.name`, `namespace`*   | Number of reconciliation retries                                     |
| `events.received`                        | counter | `controller.name`, `event`, `action`, `namespace`* | Number of Kubernetes events received by the controller  |
| `events.delete`                          | counter | `controller.name`, `namespace`*   | Number of resource deletion events processed                         |

\* `namespace` tag is only included when `withNamespaceAsTag()` is enabled.

The execution timer uses explicit SLO boundaries (10ms, 50ms, 100ms, 250ms, 500ms, 1s, 2s, 5s, 10s, 30s) to ensure
compatibility with `histogram_quantile()` queries in Prometheus. This is important when using the OTLP registry, where
`publishPercentileHistogram()` would otherwise produce Base2 Exponential Histograms that are incompatible with classic
`_bucket` queries.

> **Note on Prometheus metric names**: The exact Prometheus metric name suffix depends on the `MeterRegistry` in use.
> For `PrometheusMeterRegistry` the timer is exposed as `reconciliations_execution_duration_seconds_*`. For
> `OtlpMeterRegistry` (metrics exported via OpenTelemetry Collector), it is exposed as
> `reconciliations_execution_duration_milliseconds_*`.

#### Grafana Dashboard

A ready-to-use Grafana dashboard is available at
[`observability/josdk-operator-metrics-dashboard.json`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/observability/josdk-operator-metrics-dashboard.json).
It visualizes all of the metrics listed above, including reconciliation throughput, error rates, queue depth, active
executions, resource counts, and execution duration histograms and heatmaps.

The dashboard is designed to work with metrics exported via OpenTelemetry Collector to Prometheus, as set up by the
observability sample (see below).

#### Exploring metrics end-to-end

The
[`metrics-processing` sample operator](https://github.com/java-operator-sdk/java-operator-sdk/tree/main/sample-operators/metrics-processing)
includes a full end-to-end test,
[`MetricsHandlingE2E`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/metrics-processing/src/test/java/io/javaoperatorsdk/operator/sample/metrics/MetricsHandlingE2E.java),
that:

1. Installs a local observability stack (Prometheus, Grafana, OpenTelemetry Collector) via
   `observability/install-observability.sh`. That imports also the Grafana dashboards.
2. Runs two reconcilers that produce both successful and failing reconciliations over a sustained period
3. Verifies that the expected metrics appear in Prometheus

This is a good starting point for experimenting with the metrics and the Grafana dashboard in a real cluster without
having to deploy your own operator.

### MicrometerMetrics (Deprecated)

> **Deprecated**: `MicrometerMetrics` (V1) is deprecated as of JOSDK 5.3.0. Use `MicrometerMetricsV2` instead.
> V1 attaches resource-specific metadata (name, namespace, etc.) as tags to every meter, which causes unbounded
> cardinality growth and can lead to performance issues in your metrics backend.

The legacy `MicrometerMetrics` implementation is still available. To create an instance that behaves as it historically
has:

```java
MeterRegistry registry; // initialize your registry implementation
Metrics metrics = MicrometerMetrics.newMicrometerMetricsBuilder(registry).build();
```

To collect metrics on a per-resource basis, deleting the associated meters after 5 seconds when a resource is deleted,
using up to 2 threads:

```java
MicrometerMetrics.newPerResourceCollectingMicrometerMetricsBuilder(registry)
        .withCleanUpDelayInSeconds(5)
        .withCleaningThreadNumber(2)
        .build();
```

#### Operator SDK metrics (V1)

The V1 micrometer implementation records the following metrics:

| Meter name                                                  | Type           | Tag names                                                                           | Description                                                                                            |
|-------------------------------------------------------------|----------------|-------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| operator.sdk.reconciliations.executions.`<reconciler name>` | gauge          | group, version, kind                                                                | Number of executions of the named reconciler                                                           |
| operator.sdk.reconciliations.queue.size.`<reconciler name>` | gauge          | group, version, kind                                                                | How many resources are queued to get reconciled by named reconciler                                    |
| operator.sdk.`<map name>`.size                              | gauge map size |                                                                                     | Gauge tracking the size of a specified map (currently unused but could be used to monitor caches size) |
| operator.sdk.events.received                                | counter        | `<resource metadata>`, event, action                                                | Number of received Kubernetes events                                                                   |
| operator.sdk.events.delete                                  | counter        | `<resource metadata>`                                                               | Number of received Kubernetes delete events                                                            |
| operator.sdk.reconciliations.started                        | counter        | `<resource metadata>`, reconciliations.retries.last, reconciliations.retries.number | Number of started reconciliations per resource type                                                    |
| operator.sdk.reconciliations.failed                         | counter        | `<resource metadata>`, exception                                                    | Number of failed reconciliations per resource type                                                     |
| operator.sdk.reconciliations.success                        | counter        | `<resource metadata>`                                                               | Number of successful reconciliations per resource type                                                 |
| operator.sdk.controllers.execution.reconcile                | timer          | `<resource metadata>`, controller                                                   | Time taken for reconciliations per controller                                                          |
| operator.sdk.controllers.execution.cleanup                  | timer          | `<resource metadata>`, controller                                                   | Time taken for cleanups per controller                                                                 |
| operator.sdk.controllers.execution.reconcile.success        | counter        | controller, type                                                                    | Number of successful reconciliations per controller                                                    |
| operator.sdk.controllers.execution.reconcile.failure        | counter        | controller, exception                                                               | Number of failed reconciliations per controller                                                        |
| operator.sdk.controllers.execution.cleanup.success          | counter        | controller, type                                                                    | Number of successful cleanups per controller                                                           |
| operator.sdk.controllers.execution.cleanup.failure          | counter        | controller, exception                                                               | Number of failed cleanups per controller                                                               |

All V1 metrics start with the `operator.sdk` prefix. `<resource metadata>` refers to resource-specific metadata and
depends on the considered metric and how the implementation is configured: `group?, version, kind, [name, namespace?],
scope` where tags in square brackets (`[]`) won't be present when per-resource collection is disabled and tags followed
by a question mark are omitted if the value is empty. In the context of controllers' execution metrics, these tag names
are prefixed with `resource.`.

### Aggregated Metrics

The `AggregatedMetrics` class provides a way to combine multiple metrics providers into a single metrics instance using
the composite pattern. This is particularly useful when you want to simultaneously collect metrics data from different
monitoring systems or providers.

You can create an `AggregatedMetrics` instance by providing a list of existing metrics implementations:

```java
// create individual metrics instances
Metrics micrometerMetrics = MicrometerMetrics.withoutPerResourceMetrics(registry);
Metrics customMetrics = new MyCustomMetrics();
Metrics loggingMetrics = new LoggingMetrics();

// combine them into a single aggregated instance
Metrics aggregatedMetrics = new AggregatedMetrics(List.of(
    micrometerMetrics, 
    customMetrics, 
    loggingMetrics
));

// use the aggregated metrics with your operator
Operator operator = new Operator(client, o -> o.withMetrics(aggregatedMetrics));
```

This approach allows you to easily combine different metrics collection strategies, such as sending metrics to both
Prometheus (via Micrometer) and a custom logging system simultaneously.
