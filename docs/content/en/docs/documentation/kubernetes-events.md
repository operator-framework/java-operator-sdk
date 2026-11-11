---
title: Recording Kubernetes Events
weight: 51
---

A controller can record Kubernetes events, so that what it does shows up where users already look
for it: `kubectl describe` on the resource the event is about. Events are informational and best
effort by design, Kubernetes keeps them for a limited time and treats them as supplemental data, so
they complement the status of a resource rather than replace it.

## Recording from a reconciliation

Inside a reconciliation the recorder is available from the `Context`, bound to the primary resource:

```java
@Override
public UpdateControl<MyResource> reconcile(MyResource resource, Context<MyResource> context) {
  context.eventRecorder().normal("Reconciled", "resource reconciled");
  context.eventRecorder().warn("SomethingIsOff", "this is a warning about the resource");
  return UpdateControl.noUpdate();
}
```

For more control, build an `EventRecord`, which also carries an optional action, reporting
component, labels and annotations:

```java
context.eventRecorder().record(EventRecord.builder()
    .type(EventType.WARNING)
    .reason("DeploymentFailed")
    .message("could not scale the deployment")
    .action("Scaling")
    .build());
```

## Recording about another object

An event does not have to be about the primary resource. A controller reconciling a child resource
can, for example, record on the parent, so that `kubectl describe parent` shows what happened to its
children. Bind the recorder to the other object instead:

```java
var parent = context.getSecondaryResource(Parent.class).orElseThrow();
context.eventRecorder(parent).warn("ChildFailed", "could not reconcile child " + name);
```

The event references the given object as its involved object and lives in that object's namespace,
or, for a cluster scoped object, in the namespace configured for such events (see below). It is
still attributed to the controller recording it. Aggregation works per object: two children
recording the same reason and message on one parent count as repeats of a single event on the
parent, while different messages stay separate events.

## Recording outside of a reconciliation

From a background task or anywhere else no `Context` is at hand, use the recorder bound to the
controller, available from the `RegisteredController` returned when the controller is registered or
from `Operator#getRegisteredController(String)`. It records about any object:

```java
RegisteredController<MyResource> registered = operator.register(new MyReconciler());

registered.eventRecorder().record(someResource, EventRecord.normal("Noticed", "something happened"));
registered.eventRecorder().forResource(someResource).normal("Noticed", "something happened");
```

## What an event carries

The recorder fills in everything derivable from the controller and the object: the involved object
reference, the reporting controller (the controller name, or the reporting component set on the
record), the reporting instance (the leader election identity when configured, otherwise the host
name, which in a pod is the pod name), the timestamps and the count.

Repeats are aggregated, not duplicated. Events are named deterministically, after the object they
are about plus a hash of everything that identifies the event, so recording the same event again
resolves to the event already recorded and counts the occurrence on it. That is what makes `kubectl
describe` report a repeating event once, as `(x12 over 3m)`, instead of listing copies. Use
`EventRecord.Builder#key(...)` to control what counts as the same event, or configure the default
through `DefaultEventRecorder.builder(sink).keyStrategy(...)`, see the javadoc of
`EventKeyStrategy` and `EventNamingStrategy`.

Events about cluster scoped objects live in the namespace configured through
`withClusterScopedEventNamespace(...)` or the `josdk.events.cluster-scoped-namespace` property,
`default` unless configured otherwise. The API server only accepts `default` and `kube-system`.

## Failure handling

Recording is best effort by default: a failure to write the event is logged as a warning and
swallowed, and never fails the caller. To make such a failure fail the reconciliation instead, so
that it goes through the usual error handling and retries, configure the default recorder with
`failOnError(true)`. Outside of a reconciliation the exception then reaches whatever called the
recorder, for example the background task.

```java
KubernetesClient client = new KubernetesClientBuilder().build();
Operator operator = new Operator(override -> override
        .withKubernetesClient(client)
        .withEventRecorder(DefaultEventRecorder.builder(new DefaultEventSink(client))
            .failOnError(true)
            .build()));
```

## Custom recorders

To assemble events differently or send them somewhere else entirely, plug in an `EventRecorder` of
your own for the whole operator with `withEventRecorder(...)`. A custom recorder is shared by all
controllers, so it must be stateless and thread safe; it implements a single method,
`record(HasMetadata regarding, EventRecord event, ControllerConfiguration<?> controller)`, and gets
the bound views for free. Extend `DefaultEventRecorder` and override `toEvent(...)` to change how
events are assembled, or implement an `EventSink` to change where the assembled events go.

## Permissions

Recording needs `get`, `create` and `patch` on `events` in the core API group in every namespace
events are recorded in, see the
[Events section of the configuration page](operations/configuration.md#events).
