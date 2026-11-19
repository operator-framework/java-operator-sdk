---
title: Version 5.7 Released!
date: 2026-11-19
author: >-
  [Attila Mészáros](https://github.com/csviri)
---

We're pleased to announce the release of Java Operator SDK v5.7.0! The headline features of this
minor version are **virtual thread support**, an upgrade to the **Fabric8 Kubernetes Client 8.0**
(with the **Vert.x 5 HTTP client** as the new default), **desired state aspects** for adding common
metadata to all managed resources, and a much more capable **event recorder**. Because of the client
upgrade, which moves to Jackson 3, this release contains **breaking changes** for operators that use
Jackson types directly — see the migration notes.

## Key Features

### Virtual threads

Reconciliation is mostly about blocking: talking to the Kubernetes API server or to external systems.
Virtual threads make such blocking calls much cheaper than platform threads, and the framework can now
be switched over to them with a single flag:

```java
Operator operator = new Operator(override -> override.withUseVirtualThreads(true));
```

The same can be set with the `josdk.use-virtual-threads` configuration property. When enabled,
reconciliations, dependent resource workflows and the framework's internal housekeeping (starting the
informers, for example) all run on virtual threads. So do the polls of the polling event sources and
the retried and rescheduled reconciliations; the Kubernetes client the framework creates also
dispatches informer and watch events on virtual threads.

Enabling virtual threads does **not** remove the concurrency limits. `withConcurrentReconciliationThreads`
and `withConcurrentWorkflowExecutorThreads` still cap the parallelism exactly as before, only the
threads backing those limits change. Since virtual threads are cheap, these limits can usually be
raised significantly compared to what is reasonable with platform threads.

A few things to keep in mind:

- Virtual threads are only supported on **Java 25 or later**. Before Java 25 a virtual thread pins its
  carrier thread while inside a `synchronized` block, which can starve the carrier pool and even
  deadlock an operator on startup ([JEP 491](https://openjdk.org/jeps/491) removed that pinning in
  Java 25). This is checked at runtime: on an older JVM a warning is logged and platform threads are
  used, so the same configuration works on any Java version the operator runs on.
- Virtual threads are daemon threads, so they don't keep the JVM running. If nothing else does, call
  the new `Operator.keepAlive()` after starting the operator (see below).
- An executor you provide yourself is always used as is, the flag has no effect on it.

#### Keeping the JVM alive

Until now the JVM was kept running as a side effect of the non-daemon threads of the operator's thread
pools. With virtual threads that is no longer the case, so `Operator.keepAlive()` starts a non-daemon
thread that keeps the JVM running until the operator is stopped:

```java
public static void main(String[] args) {
  Operator operator = new Operator(override -> override.withUseVirtualThreads(true));
  operator.register(new MyReconciler());
  operator.installShutdownHook();
  operator.start();
  operator.keepAlive();
}
```

#### Polling event sources have their own executors

To make this work, the polling event sources and the internal `TimerEventSource` no longer use a
`java.util.Timer`. Each polling event source now polls on a scheduled executor of its own, so a slow
poll of one event source never delays the polls of the others, and retried and rescheduled
reconciliations run on a separate executor that a slow poll can never delay. A
`PerResourcePollingEventSource` with many registered resources can be given an executor with more
threads:

```java
var config =
    new PerResourcePollingConfigurationBuilder<>(this::fetchResources, Duration.ofSeconds(5))
        .withExecutorService(Executors.newScheduledThreadPool(4))
        .build();
```

An executor provided that way is yours to shut down. See the
[configuration](/docs/documentation/operations/configuration/#virtual-threads) and
[eventing](/docs/documentation/eventing/#threading-of-the-polling-event-sources) documentation for
details, and the migration guide for the (small) behavioral differences.

### Fabric8 Kubernetes Client 8.0 and the Vert.x 5 HTTP client

JOSDK 5.7 is built on version 8.0.0 of the
[Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client). The most visible change
of the client is the move from Jackson 2 to **Jackson 3**: the packages changed from
`com.fasterxml.jackson` to `tools.jackson` (the annotations being the exception). If your operator
uses Jackson types directly, you will need to update your imports and dependencies.

In line with the client's own default, the `operator-framework` artifact now brings the **Vert.x 5**
based HTTP client instead of the JDK one. If you don't declare an HTTP client of your own there is
nothing to do. To keep the JDK client, or to use another implementation, exclude the default and add the
one you want:

```xml
<dependency>
  <groupId>io.javaoperatorsdk</groupId>
  <artifactId>operator-framework</artifactId>
  <exclusions>
    <exclusion>
      <groupId>io.fabric8</groupId>
      <artifactId>kubernetes-httpclient-vertx-5</artifactId>
    </exclusion>
  </exclusions>
</dependency>
<dependency>
  <groupId>io.fabric8</groupId>
  <artifactId>kubernetes-httpclient-jdk</artifactId>
  <version>8.0.0</version>
</dependency>
```

### Desired state aspects

Operators often need to mark every resource they manage in a uniform way, for example with an
`app.kubernetes.io/managed-by` label, so that these resources can be easily identified, selected or
garbage-collected later on. Instead of repeating that logic in every `desired()` implementation, a
`DesiredStateAspect` can now be registered once, at the operator level, and is applied to the desired
state of every Kubernetes dependent resource the operator manages:

```java
Operator operator = new Operator(overrider -> overrider
    .withDesiredStateAspects(List.of(
        (desired, dependentResource, context) -> desired.getMetadata().getLabels()
            .put("app.kubernetes.io/managed-by", "my-operator"))));
```

Aspects are applied in registration order, right after the desired state is computed and before it is
matched against the actual resource. The metadata an aspect adds is therefore part of the desired
state proper: if it is removed from the actual resource, or the aspect itself changes, the secondary
resources are updated accordingly on the next reconciliation. Aspects are called at most once per
dependent resource and reconciliation, are not called for external (non-Kubernetes) dependent
resources, and need to be thread-safe.

Thanks to [Martin Stefanko](https://github.com/xstefank) for this contribution!

### A more capable event recorder

The [event recorder](/docs/documentation/kubernetes-events) introduced in 5.6 got several additions,
and has a documentation page of its own now.

**Events about any object.** An event does not have to be about the primary resource. A controller
reconciling a child resource can, for example, record on the parent, so that `kubectl describe parent`
shows what happened to its children. Bind the recorder to the other object:

```java
var parent = context.getSecondaryResource(Parent.class).orElseThrow();
context.eventRecorder(parent).warn("ChildFailed", "could not reconcile child " + name);
```

Aggregation works per object: two children recording the same reason and message on one parent count
as repeats of a single event on the parent, while different messages stay separate events.

**Fail on error.** Recording an event is best effort by default: a failure to write it is logged and
swallowed. Where an event that is not recorded must not go unnoticed, the default recorder can be
configured to propagate the failure, so that the reconciliation fails and is retried like any other:

```java
KubernetesClient client = new KubernetesClientBuilder().build();
Operator operator = new Operator(override -> override
    .withKubernetesClient(client)
    .withEventRecorder(DefaultEventRecorder.builder(new DefaultEventSink(client))
        .failOnError(true)
        .build()));
```

**Configurable aggregation, owner references and naming.** `DefaultEventRecorder` can now be built with
an `EventKeyStrategy`, controlling what counts as the same event, an `EventNamingStrategy`,
controlling how events are named, and optionally sets an `ownerReference` to the object an event is
about. `EventRecord.Builder#key(...)` and `ownedByRegarding(...)` override these per event.

**Correctness fixes.** Aggregating onto an existing event now also updates its type, reason and
action, so a `Warning` repeated under the name of a `Normal` event is no longer reported as the
`Normal` one. Event names longer than the Kubernetes limit are shortened with a hash instead of being
plainly truncated, so distinct names sharing a long prefix no longer merge into one event. Events
about cluster scoped resources now carry what the API server requires to accept them in `kube-system`
(the API server only accepts such events in `default` and `kube-system`, so the documentation no
longer suggests the namespace the operator runs in).

Thanks to [Antonio Fernandez Alhambra](https://github.com/afalhambra-hivemq) for the event recorder
contributions!

### Removing the namespace index of informers

Informers keep an index from namespace to the cached resources. JOSDK never reads that index, so for
informers caching a large number of resources it can be removed to save an entry per cached resource,
which matters most together with a custom item store that caches only a reduced form of each resource:

```java
@ControllerConfiguration(informer = @Informer(withoutNamespaceIndex = true))
public class MyReconciler implements Reconciler<MyCustomResource> { }
```

The option is also available on `InformerEventSourceConfiguration.Builder` and `InformerConfiguration.Builder`,
and as the `josdk.controller.<name>.informer.without-namespace-index` property. Note that it takes
part in the informer pool identity: event sources watching the same resource type, where only one
removes the index, don't share an informer.

Thanks to [Antonio Fernandez Alhambra](https://github.com/afalhambra-hivemq) for this contribution too!

## Bug Fixes

- **`comparableResourceVersions` is kept when a dependent resource's informer configuration is
  copied**, it used to be lost.
- **The `version.properties` resource was renamed to `operator-sdk-version.properties`** to avoid
  collisions with resources of the same name on the classpath.

## Migration Notes

### Fabric8 Kubernetes Client 8.0 / Jackson 3

This is the only source-incompatible change for most operators. Besides the package change
(`com.fasterxml.jackson` to `tools.jackson`), `JsonProcessingException` was replaced by the unchecked
`JacksonException`, some `JsonNode` methods were renamed (`fields()` to `properties()`, `asText()` to
`asString()`), and `ObjectMapper` instances are created using builders. Also, calls to
`withShardSelector(null)` no longer compile because of an additional overload, cast the argument:
`withShardSelector((String) null)`.

### Default HTTP client is now Vert.x 5

If your operator already replaced the default HTTP client by excluding `kubernetes-httpclient-jdk`,
update the exclusion to `kubernetes-httpclient-vertx-5`, otherwise it ends up on the classpath next to
your implementation. This is especially important with the Vert.x 4 based client, since Vert.x 4 and
Vert.x 5 cannot be used together.

### Polling and timer event sources

An executor provided to a `PerResourcePollingConfigurationBuilder` is no longer shut down by the event
source, and `PerResourcePollingConfiguration.executorService()` returns `null` when none was provided.
`PollingEventSource(Class, PollingConfiguration)` and `TimerEventSource(String, boolean)` are
deprecated; use the constructors that take an `EventSourceContext` and an executor supplier respectively.

### Low level event recorder API

The low level event recorder API changed to support events about any object: a custom
`EventRecorder` now implements `record(HasMetadata regarding, EventRecord event, Context<?> context)`
and a subclass of `DefaultEventRecorder` overrides `toEvent(HasMetadata, EventRecord, Context<?>)`.
Reconcilers that only use `context.eventRecorder()` are not affected.

See the [migration guide](/docs/migration/v5-7-migration) for all the details.

## Getting Started

```xml
<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework</artifactId>
    <version>5.7.0</version>
</dependency>
```

## All Changes

See the [comparison view](https://github.com/operator-framework/java-operator-sdk/compare/v5.6.4...v5.7.0)
for the full list of changes.

## Feedback

Please report issues or suggest improvements on our
[GitHub repository](https://github.com/operator-framework/java-operator-sdk/issues).

Happy operator building! 🚀
