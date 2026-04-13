---
title: Version 5.3 Released!
date: 2026-03-13
author: >-
  [Attila Mészáros](https://github.com/csviri)
---

We're pleased to announce the release of Java Operator SDK v5.3.0! This minor version brings
two headline features — read-cache-after-write consistency and a new metrics implementation —
along with a configuration adapter system, MDC improvements, and a number of smaller improvements
and cleanups.

## Key Features

### Read-cache-after-write Consistency and Event Filtering

This is the headline feature of 5.3. Informer caches are inherently eventually consistent: after
your reconciler updates a resource, there is a window of time before the change is visible in the
cache. This can cause subtle bugs, particularly when storing allocated values in the status
sub-resource and reading them back in the next reconciliation.

From 5.3.0, the framework provides two guarantees when you use
[`ResourceOperations`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ResourceOperations.java)
(accessible from `Context`):

1. **Read-after-write**: Reading from the cache after your update — even within the same
   reconciliation — returns at least the version of the resource from your update response.
2. **Event filtering**: Events produced by your own writes no longer trigger a redundant
   reconciliation.

`UpdateControl` and `ErrorStatusUpdateControl` use this automatically. Secondary resources benefit
via `context.resourceOperations()`:

```java
public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {

    ConfigMap managedConfigMap = prepareConfigMap(webPage);
    // update is cached and will suppress the resulting event
    context.resourceOperations().serverSideApply(managedConfigMap);

    // fresh resource instantly available from the cache
    var upToDateResource = context.getSecondaryResource(ConfigMap.class);

    makeStatusChanges(webPage);
    // UpdateControl also uses this by default
    return UpdateControl.patchStatus(webPage);
}
```

If your reconciler relied on being re-triggered by its own writes, a new `reschedule()` method on
`UpdateControl` lets you explicitly request an immediate re-queue.

> **Note**: `InformerEventSource.list(..)` bypasses the additional caches and will not reflect
> in-flight updates. Use `context.getSecondaryResources(..)` or `InformerEventSource.get(ResourceID)`
> instead.

See the related [blog post](../news/read-after-write-consistency.md) and [reconciler docs](/docs/documentation/reconciler#read-cache-after-write-consistency-and-event-filtering) for details.

### MicrometerMetricsV2

A new micrometer-based `Metrics` implementation designed with low cardinality in mind. All meters
are scoped to the controller, not to individual resources, avoiding unbounded cardinality growth as
resources come and go.

```java
MeterRegistry registry; // initialize your registry
Metrics metrics = MicrometerMetricsV2.newBuilder(registry).build();
Operator operator = new Operator(client, o -> o.withMetrics(metrics));
```

Optionally attach a `namespace` tag to per-reconciliation counters (disabled by default):

```java
Metrics metrics = MicrometerMetricsV2.newBuilder(registry)
        .withNamespaceAsTag()
        .build();
```

The full list of meters:

| Meter | Type | Description |
|---|---|---|
| `reconciliations.active` | gauge | Reconciler executions currently running |
| `reconciliations.queue` | gauge | Resources queued for reconciliation |
| `custom_resources` | gauge | Resources tracked by the controller |
| `reconciliations.execution.duration` | timer | Execution duration with explicit histogram buckets |
| `reconciliations.started.total` | counter | Reconciliations started |
| `reconciliations.success.total` | counter | Successful reconciliations |
| `reconciliations.failure.total` | counter | Failed reconciliations |
| `reconciliations.retries.total` | counter | Retry attempts |
| `events.received` | counter | Kubernetes events received |

The execution timer uses explicit bucket boundaries (10ms–30s) to ensure compatibility with
`histogram_quantile()` in both `PrometheusMeterRegistry` and `OtlpMeterRegistry`.

A ready-to-use **Grafana dashboard** is included at
[`observability/josdk-operator-metrics-dashboard.json`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/observability/josdk-operator-metrics-dashboard.json).

The
[`operations` sample operator](https://github.com/java-operator-sdk/java-operator-sdk/tree/main/sample-operators/operations)
provides a complete end-to-end setup with Prometheus, Grafana, and an OpenTelemetry Collector,
installable via `observability/install-observability.sh`. This is a good starting point for
verifying metrics in a real cluster.

> **Deprecated**: The original `MicrometerMetrics` (V1) is deprecated as of 5.3.0. It attaches
> resource-specific metadata as tags to every meter, causing unbounded cardinality. Migrate to
> `MicrometerMetricsV2`.

See the [observability docs](/docs/documentation/observability#micrometermetricsv2) for the full
reference.

### Configuration Adapters

A new `ConfigLoader` bridges any key-value configuration source to the JOSDK operator and
controller configuration APIs. This lets you drive operator behaviour from environment variables,
system properties, YAML files, or any config library without writing glue code by hand.

The default instance stacks environment variables over system properties out of the box:

```java
Operator operator = new Operator(ConfigLoader.getDefault().applyConfigs());
```

Built-in providers: `EnvVarConfigProvider`, `PropertiesConfigProvider`, `YamlConfigProvider`,
and `AggregatePriorityListConfigProvider` for explicit priority ordering.

`ConfigProvider` is a single-method interface, so adapting any config library (MicroProfile Config,
SmallRye Config, etc.) takes only a few lines:

```java
public class SmallRyeConfigProvider implements ConfigProvider {
    private final SmallRyeConfig config;

    @Override
    public <T> Optional<T> getValue(String key, Class<T> type) {
        return config.getOptionalValue(key, type);
    }
}
```

Pass the results when constructing the operator and registering reconcilers:

```java
var configLoader = new ConfigLoader(new SmallRyeConfigProvider(smallRyeConfig));

Operator operator = new Operator(configLoader.applyConfigs());
operator.register(new MyReconciler(), configLoader.applyControllerConfigs(MyReconciler.NAME));
```

See the [configuration docs](/docs/documentation/configuration#loading-configuration-from-external-sources)
for the full list of supported keys.

> **Note**: This new configuration mechanism is useful when using the SDK by itself. Framework (Spring Boot, Quarkus, …)
> integrations usually provide their own configuration mechanisms that should be used instead of this new mechanism.

### MDC Improvements

**MDC in workflow execution**: MDC context is now propagated through workflow (dependent resource
graph) execution threads, not just the top-level reconciler thread. Logging from dependent
resources now carries the same contextual fields as the primary reconciliation.

**`NO_NAMESPACE` for cluster-scoped resources**: Instead of omitting the `resource.namespace` MDC
key for cluster-scoped resources, the framework now emits `MDCUtils.NO_NAMESPACE`. This makes log
queries for cluster-scoped resources reliable.

### De-duplicated Secondary Resources from Context

When multiple event sources manage the same resource type, `context.getSecondaryResources(..)` now
returns a de-duplicated stream. When the same resource appears from more than one source, only the
copy with the highest resource version is returned.

### Record Desired State in Context

Dependent resources now record their desired state in the `Context` during reconciliation. This allows reconcilers and
downstream dependents in a workflow to inspect what a dependent resource computed as its desired state and guarantees
that the desired state is computed only once per reconciliation.

### Informer Health Checks

Informer health checks no longer rely on `isWatching`. For readiness and startup probes, you should
primarily use `hasSynced`. Once an informer has started, `isWatching` is not suitable for liveness
checks.

## Additional Improvements

- **Annotation removal using locking**: Finalizer and annotation management no longer uses
  `createOrReplace`; a locking-based `createOrUpdate` avoids conflicts under concurrent updates.
- **`KubernetesDependentResource` uses `ResourceOperations` directly**, removing an indirection
  layer and automatically benefiting from the read-after-write guarantees.
- **Skip namespace deletion in JUnit extension**: The JUnit extension now supports a flag to skip
  namespace deletion after a test run, useful for debugging CI failures.
- **`ManagedInformerEventSource.getCachedValue()` deprecated**: Use
  `context.getSecondaryResource(..)` instead.
- **Improved event filtering for multiple parallel updates**: The filtering algorithm now handles
  cases where multiple parallel updates are in flight for the same resource.
- `exitOnStopLeading` is being prepared for removal from the public API.

## Migration Notes

### JUnit module rename

```xml
<!-- before -->
<artifactId>operator-framework-junit-5</artifactId>
<!-- after -->
<artifactId>operator-framework-junit</artifactId>
```

### `Metrics` interface renames

| v5.2 | v5.3 |
|---|---|
| `reconcileCustomResource` | `reconciliationSubmitted` |
| `reconciliationExecutionStarted` | `reconciliationStarted` |
| `reconciliationExecutionFinished` | `reconciliationSucceeded` |
| `failedReconciliation` | `reconciliationFailed` |
| `finishedReconciliation` | `reconciliationFinished` |
| `cleanupDoneFor` | `cleanupDone` |
| `receivedEvent` | `eventReceived` |

`reconciliationFinished(..)` is extended with `RetryInfo`. `monitorSizeOf(..)` is removed.

### `ResourceAction` relocated

`ResourceAction` in `io.javaoperatorsdk.operator.processing.event.source.controller` has been
removed. Use `io.javaoperatorsdk.operator.processing.event.source.ResourceAction` instead.

See the full [migration guide](/docs/migration/v5-3-migration) for details.

## Getting Started

```xml
<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework</artifactId>
    <version>5.3.0</version>
</dependency>
```

## All Changes

See the [comparison view](https://github.com/operator-framework/java-operator-sdk/compare/v5.2.0...v5.3.0)
for the full list of changes.

## Feedback

Please report issues or suggest improvements on our
[GitHub repository](https://github.com/operator-framework/java-operator-sdk/issues).

Happy operator building! 🚀
