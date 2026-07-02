---
title: Version 5.4 Released!
date: 2026-07-03
author: >-
  [Attila Mészáros](https://github.com/csviri)
---

We're pleased to announce the release of Java Operator SDK v5.4.0! This minor version adds
sharding support for horizontally splitting a workload across operator replicas, finer-grained
control over event filtering, richer secondary-resource lookups on `Context`, and a smarter retry
scheduler — along with a number of smaller improvements, deprecations, and a Fabric8 client upgrade.

## Key Features

### Shard Selector Support

Large clusters sometimes need the same operator to run as multiple replicas, each responsible for a
subset ("shard") of the resources. From 5.4.0 you can configure a **shard selector** — a second,
Kubernetes-style label selector that is applied *in addition to* the normal label selector (the two
are combined with logical AND).

```java
@ControllerConfiguration(informer = @Informer(shardSelector = "shardRange(object.metadata.uid, '0x0000000000000000', '0x8000000000000000')"))
public class MyReconciler implements Reconciler<MyCustomResource> { ... }
```

It can also be set programmatically or via configuration:

```java
ControllerConfigurationOverrider.override(config)
    .withShardSelector("shardRange(object.metadata.uid, '0x0000000000000000', '0x8000000000000000')")
    .build();
```

`withShardSelector(String)` is available on `InformerConfiguration.Builder`,
`InformerEventSourceConfiguration.Builder`, and `ControllerConfigurationOverrider`, and via the
`josdk.controller.<name>.shard-selector` configuration key. This feature relies on Fabric8 client
support and requires the 7.8.0 baseline shipped with this release.

### Opting Out of Default Event Filters

By default, JOSDK applies a set of internal update filters to a controller's own event source:
generation-aware filtering, and finalizer-needed/marked-for-deletion handling. These are the right
default for most operators, but occasionally you need full control over exactly which updates
trigger a reconciliation.

The new `defaultFilters` flag (default `true`) lets you turn them off. When set to `false`, your
`@Informer(onUpdateFilter = ...)` becomes the *sole* update filter — and if you don't set one, all
updates pass through:

```java
@ControllerConfiguration(
    defaultFilters = false,
    informer = @Informer(onUpdateFilter = MyUpdateFilter.class))
public class MyReconciler implements Reconciler<MyCustomResource> { ... }
```

The internal filter building blocks in `InternalEventFilters` are now public, so you can re-compose
the parts you still want alongside your custom logic:

```java
OnUpdateFilter<MyCustomResource> composed =
    InternalEventFilters.<MyCustomResource>onUpdateGenerationAware(true)
        .or((newRes, oldRes) -> /* custom trigger, e.g. a specific annotation present */);
```

`withDefaultFilters(boolean)` is also available on `ControllerConfigurationOverrider`.

### By-name Secondary Resource Lookup on Context

Looking up a single secondary resource by name previously meant streaming all secondaries and
filtering them yourself. `Context` now offers direct by-name lookups:

```java
// name + namespace from a specific named event source
Optional<Secret> secret =
    context.getSecondaryResource(Secret.class, "my-event-source", "cred-secret", "my-ns");

// namespace inferred from the primary resource
Optional<Secret> secret2 =
    context.getSecondaryResource(Secret.class, "my-event-source", "cred-secret");

// stream all secondaries of a type from a specific named event source
Stream<ConfigMap> configMaps =
    context.getSecondaryResourcesAsStream(ConfigMap.class, "cm-event-source");
```

When the underlying event source is a cache, these hit the cache directly (and are read-cache-after-write
consistent); otherwise they fall back to filtering the full secondary set. The stream overload works
for **non-Kubernetes** secondary types too — its type bound was relaxed so it is no longer restricted
to `HasMetadata` — making it usable with external/polling event sources.

### Retry Interval Honored Under Frequent Events

Previously, when a failed reconciliation was triggered by an incoming event while a retry was already
scheduled, the incoming reconciliation could consume a retry attempt and advance the retry counter.
Under a steady stream of external events this meant the configured retry interval was effectively
ignored and the operator could exhaust its retries far too quickly.

From 5.4.0, an event-driven reconciliation that fails while there is still plenty of time left in
the current retry window **preserves the existing retry deadline** and does *not* consume a retry
attempt — it simply re-schedules on the original deadline. A retry attempt is only counted once the
scheduled deadline is imminent (within 5 seconds). This is transparent to users; the configured
retry interval is now genuinely honored even under frequent events.

A new `RetryExecution#remainingDurationUntilNextRetry()` returning `Optional<Duration>` supports this
behavior.

### Reconciling Dropped Secondary References

On an **update** event for a secondary resource, the framework now invokes your
`SecondaryToPrimaryMapper` for **both the old and the new version** of the resource and reconciles
the union of the results. This means primaries that the secondary *used to* reference but no longer
does — including partial subset changes — are also reconciled and can revert to their expected state.

> **Note**: Because the mapper may now be called on an *older* version of a resource, a
> `SecondaryToPrimaryMapper` implementation must be a **pure function of the resource passed to it**
> and must not rely on external "current" state.

## Additional Improvements

- **Owner-reference mappers match on group only**: `Mappers.fromOwnerReferences(apiVersion, kind, ...)`
  now matches on kind + group and ignores the version. Owner references written under one CRD version
  (e.g. `.../v1`) still resolve correctly after the served/storage version changes (e.g. to `.../v2`).
- **`GenericRetry#setMaxInterval(Duration)`**: a `Duration`-based overload alongside the existing
  millis-based one, e.g. `new GenericRetry().setMaxInterval(Duration.ofMinutes(5))`.

## Migration Notes

### Shutdown hook: `installShutdownHook(Duration)` deprecated

`Operator#installShutdownHook(Duration)` is deprecated for removal. Its `Duration` argument is now
ignored. This also fixes a deadlock that could occur when `stop()` was called from a JVM shutdown
hook while leader election was active.

```java
// before
operator.installShutdownHook(Duration.ofSeconds(30));

// after — timeout comes from ConfigurationService
operator.installShutdownHook();
```

Configure the graceful shutdown timeout via
`ConfigurationServiceOverrider#withReconciliationTerminationTimeout(Duration)`. Unlike the old
variant, the no-arg `installShutdownHook()` installs regardless of whether leader election is enabled,
and is idempotent.

### Instance-based `Mappers.fromOwnerReferences` deprecated

The overloads taking a `HasMetadata` **instance** are deprecated for removal. Pass the primary
resource **class** instead:

```java
// before
Mappers.fromOwnerReferences(primaryResource);
Mappers.fromOwnerReferences(primaryResource, clusterScoped);

// after
Mappers.fromOwnerReferences(MyPrimary.class);
Mappers.fromOwnerReferences(MyPrimary.class, clusterScoped);
```

## Getting Started

```xml
<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework</artifactId>
    <version>5.4.0</version>
</dependency>
```

## All Changes

See the [comparison view](https://github.com/operator-framework/java-operator-sdk/compare/v5.3.0...v5.4.0)
for the full list of changes.

## Feedback

Please report issues or suggest improvements on our
[GitHub repository](https://github.com/operator-framework/java-operator-sdk/issues).

Happy operator building! 🚀
