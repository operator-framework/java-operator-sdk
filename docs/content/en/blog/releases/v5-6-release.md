---
title: Version 5.6 Released!
date: 2026-09-02
author: >-
  [Attila Mészáros](https://github.com/csviri)
---

We're pleased to announce the release of Java Operator SDK v5.6.0! The headline features of this
minor version are **informer pooling** — informers are now shared between controllers and event
sources instead of one per event source — and a first-class **event recorder** for writing Kubernetes
events. There are **no breaking API changes**.

## Key Features

### Informer pooling: shared informers across controllers

Until now every `InformerEventSource` created its own `SharedIndexInformer`. An operator whose
controllers all watch the same secondary type — `ConfigMap` and `Secret` being the usual suspects —
opened one watch connection and kept one full cache **per controller** for the very same resources.

Informers are now handed out by an `InformerPool` obtained from the `ConfigurationService`. Event
sources whose effective informer configuration is equivalent are backed by a single informer, which
cuts both memory usage and the number of watch connections opened against the API server. Two event
sources share an informer when they match on all of:

- the `KubernetesClient` they watch through, compared **by instance** (two clients are never
  considered equivalent even when they point at the same API server — they may differ in
  credentials, impersonation or TLS material),
- the resource type, or the group/version/kind for generic resources,
- the watched namespace,
- the label, field and shard selectors,
- the configured item store.

Two things are deliberately *not* part of that identity. The `informerListLimit` is excluded, so
event sources that disagree only on it still share an informer, keeping the limit of whichever one
created it and logging a warning. Indexers are excluded because they can be added to a running
informer: they are registered under a name qualified with the controller and event source that added
them, so index names stay private to an event source and cannot collide, and they are removed again
when that event source releases the informer. You keep looking up indexes by the name you
registered.

The pool is reference counted: the informer is created on first use and stopped only once the last
event source using it is de-registered (or its controller stops). Registering an event source
dynamically against an already running shared informer needs no special handling — the cache
contents are replayed to the newly added handler.

Two strategies ship, and both are selected through the `ConfigurationService`:

```java
// opt out of sharing, restoring the pre-5.6 behavior of one informer per event source
Operator operator = new Operator(overrider ->
    overrider.withInformerPool(new NonSharingInformerPool()));
```

`DefaultInformerPool` shares as described above and is the default; `NonSharingInformerPool` creates
a dedicated informer per event source. A custom strategy extends `AbstractInformerPool`, which
already creates the informers from an `InformerClassifier`, starts them and waits for their caches to
sync, leaving the subclass only the question of whether and when an informer is shared.

> **Note on stability**: the pooling itself is production ready, but the configuration API around it
> (`ConfigurationService#informerPool`, `withInformerPool`) is marked `@Experimental` and may still
> change in a non-backwards-compatible way.

See the [eventing documentation](/docs/documentation/eventing) for details.

### Kubernetes event recorder

JOSDK now knows how to record Kubernetes events, so that a reconciler can surface what it is doing
where users already look for it — `kubectl describe`. Inside a reconciliation the recorder is
available from the `Context`, already bound to the primary resource:

```java
@Override
public UpdateControl<MyResource> reconcile(MyResource resource, Context<MyResource> context) {
  context.eventRecorder().normal("Reconciled", "resource reconciled");
  context.eventRecorder().warn("SomethingIsOff", "this is a warning about the resource");
  return UpdateControl.noUpdate();
}
```

For more control, build an `EventRecord`, which also carries an optional action, reporting component,
labels and annotations:

```java
context.eventRecorder().record(EventRecord.builder()
    .type(EventType.WARNING)
    .reason("DeploymentFailed")
    .message("could not scale the deployment")
    .action("Scaling")
    .build());
```

Outside of the reconciliation loop — from a status listener or a background task, or about an object
other than the primary — use the unbound form from
`RegisteredController#eventRecorder()`, which is scoped to the controller rather than to a
reconciliation:

```java
EventRecorder recorder = registeredController.eventRecorder();
recorder.record(someOtherResource, EventRecord.normal("Noticed", "something happened"));
```

Two properties are worth calling out:

- **Recording is best effort.** A failure to write the event is logged and swallowed, and never fails
  the caller — a controller that fails to reconcile because it could not write an event is strictly
  worse than one that records nothing.
- **Repeats are aggregated, not duplicated.** Events are named deterministically, after the object
  they are about plus a hash of everything that identifies the event. Recording the same event again
  therefore resolves to the event already recorded and patches its `count` and `lastTimestamp`, which
  is what makes `kubectl describe` report a repeating event once as `(x12 over 3m)` instead of
  filling the event list with copies. Use `EventRecord.Builder#key(...)` to control what counts as
  "the same" event.

Events live in a namespace of their own: that of the object they are about, and for cluster scoped
objects the namespace configured via `withClusterScopedEventNamespace(...)` or the
`josdk.events.cluster-scoped-namespace` property, defaulting to `default`.

Recording needs `get`, `create` and `patch` on `events` in the core (`""`) API group — all three,
since an event is looked up before it is created and patched when it already exists. The generic Helm
chart's `ClusterRole` grants them out of the box. If you write your own RBAC, note that a namespaced
`Role` has to grant the permission in *every* namespace events are recorded in, including the
cluster-scoped event namespace.

Thanks to [Tan Qi](https://github.com/TQJADE) for contributing this feature!

### Detecting dependent resource API version changes (experimental)

When a dependent resource's CRD gains a new API version and the operator is upgraded to target it,
comparing the actual resource's `apiVersion` with the desired one detects nothing: the API server
serves a resource under the requested version regardless of how it is stored, so the comparison
always trivially matches. `KubernetesDependentResource` consequently ignores `apiVersion` when
matching.

To still force a one-time update after such an upgrade, `@KubernetesDependent` gained an opt-in
`detectApiVersionChange` flag:

```java
@KubernetesDependent(detectApiVersionChange = true)
public class MyDependentResource extends CRUDKubernetesDependentResource<ConfigMap, MyPrimary> {
  // ...
}
```

When enabled, JOSDK records the API version it applies in the
`javaoperatorsdk.io/last-applied-api-version` annotation, and the matcher reports a mismatch when
that marker differs from the version currently in use — which also covers resources predating this
feature and therefore carrying no marker. After the update the marker matches again, so no
reconciliation loop results. It is disabled by default, and is not a replacement for Kubernetes'
[StorageVersionMigration](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/storage-version-migration/),
which addresses the orthogonal concern of migrating the stored representation.

Thanks to [한의준](https://github.com/hej090224) for this contribution!

## Bug Fixes

### Recently written external resources are no longer lost from the cache

An update of the whole resource set of a primary — a poll result or a received event — might have
been created *before* the reconciler wrote a resource, and therefore not contain it yet. Since such
updates are treated as the full actual state, the write was lost from the cache, and the next
reconciliation created a duplicate of an already created resource or repeated an already executed
update.

Writes are now marked as unconfirmed and retained for the next update if that update either does not
contain the resource at all (the expected case for a create) or still contains a state that a write
replaced. Every state replaced since the last update is kept, since the reconciler may write the same
resource several times in between. Any other state is treated as a change made outside of the
reconciler and accepted as actual, and marks are dropped on the first update, so a resource really
deleted or changed meanwhile is not retained indefinitely.

For external state bulk dependent resources that take longer to become visible, the recommended
approach remains to resolve the actual resources from the state resources in
`BulkDependentResource.getSecondaryResources` — the state resources are managed by an
`InformerEventSource` and are therefore always up-to-date regarding the operator's own changes. This
is now documented and shown in the
[integration test](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/externalstate/externalstatebulkdependent).

## Additional Improvements

- **Less redundant work on informer event paths**: `Mappers#fromMetadata` no longer resolves the
  primary `GroupVersionKind` on every secondary event, `SecondaryToPrimaryFromDefaultAnnotation` no
  longer builds a whole new mapper per invocation, and `InformerEventSource#start` no longer walks
  the entire informer cache to seed a primary-to-secondary index that is the no-op implementation
  (i.e. whenever a `primaryToSecondaryMapper` is configured) — that walk was pure startup latency
  proportional to the number of cached secondaries.
- **The JavaPoet dependency is gone.** It was only used by the annotation processor to turn a
  resolved `TypeMirror` into its fully qualified name, which the standard annotation processing API
  does on its own. JavaPoet has not been released since 2024, which made it a problem for users whose
  organizations do not approve unmaintained dependencies.
- Internal clean-ups with no API impact: the Kubernetes resource matchers were simplified, the
  informer target client is resolved without a downcast, `ResourceState` now owns the
  trigger-on-all-events flag, and the workflow result map is sized from `Workflow#size`.
- Additional test coverage, including informer retry after a custom resource deserialization problem,
  and integration tests for informer sharing, dynamic registration and de-registration against both
  pool strategies.
- Dependency updates: Micrometer 1.17.1, JUnit BOM 6.1.3, Jetty 12.1.12, Apache Maven 3.9.16,
  OpenRewrite 8.90.4 and others.

## Migration Notes

There are **no breaking API changes**; existing code compiles and runs unchanged. Two things are
worth being aware of:

### Informers are shared by default

This is a behavioral change: event sources with equivalent configuration now share one informer and
one cache. This should be transparent, but if your operator depends on having a dedicated informer
per event source, opt out with `withInformerPool(new NonSharingInformerPool())`.

### `InformerEventSource(configuration, context)` is deprecated

Since the informer is now created by the pool from the configuration, the `EventSourceContext` is no
longer needed to resolve the client. The two-argument constructor is deprecated for removal; drop the
context argument:

```java
// before
new InformerEventSource<>(configuration, context);

// after
new InformerEventSource<>(configuration);
```

## Getting Started

```xml
<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework</artifactId>
    <version>5.6.0</version>
</dependency>
```

## All Changes

See the [comparison view](https://github.com/operator-framework/java-operator-sdk/compare/v5.5.1...v5.6.0)
for the full list of changes.

## Feedback

Please report issues or suggest improvements on our
[GitHub repository](https://github.com/operator-framework/java-operator-sdk/issues).

Happy operator building! 🚀
