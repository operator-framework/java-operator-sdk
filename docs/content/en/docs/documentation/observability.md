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

### Micrometer implementation

The micrometer implementation is typically created using one of the provided factory methods which, depending on which
is used, will return either a ready to use instance or a builder allowing users to customize how the implementation
behaves, in particular when it comes to the granularity of collected metrics. It is, for example, possible to collect
metrics on a per-resource basis via tags that are associated with meters. This is the default, historical behavior but
this will change in a future version of JOSDK because this dramatically increases the cardinality of metrics, which
could lead to performance issues.

To create a `MicrometerMetrics` implementation that behaves how it has historically behaved, you can just create an
instance via:

```java
MeterRegistry registry; // initialize your registry implementation
Metrics metrics = MicrometerMetrics.newMicrometerMetricsBuilder(registry).build();
```

The class provides factory methods which either return a fully pre-configured instance or a builder object that will
allow you to configure more easily how the instance will behave. You can, for example, configure whether the
implementation should collect metrics on a per-resource basis, whether associated meters should be removed when a
resource is deleted and how the clean-up is performed. See the relevant classes documentation for more details.

For example, the following will create a `MicrometerMetrics` instance configured to collect metrics on a per-resource
basis, deleting the associated meters after 5 seconds when a resource is deleted, using up to 2 threads to do so.

```java
MicrometerMetrics.newPerResourceCollectingMicrometerMetricsBuilder(registry)
        .withCleanUpDelayInSeconds(5)
        .withCleaningThreadNumber(2)
        .build();
```

### Operator SDK metrics

The micrometer implementation records the following metrics:

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

As you can see all the recorded metrics start with the `operator.sdk` prefix. `<resource metadata>`, in the table above,
refers to resource-specific metadata and depends on the considered metric and how the implementation is configured and
could be summed up as follows: `group?, version, kind, [name, namespace?], scope` where the tags in square
brackets (`[]`) won't be present when per-resource collection is disabled and tags followed by a question mark are
omitted if the associated value is empty. Of note, when in the context of controllers' execution metrics, these tag
names are prefixed with `resource.`. This prefix might be removed in a future version for greater consistency.

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
