---
title: Features
description: Features of the SDK
layout: docs
permalink: /docs/features
---

# Features

Java Operator SDK is a high level framework and related tooling in order to facilitate implementation of Kubernetes
operators. The features are by default following the best practices in an opinionated way. However, feature flags and
other configuration options are provided to fine tune or turn off these features.

## Controller Execution in a Nutshell

Controller execution is always triggered by an event. Events typically come from the custom resource
(i.e. custom resource is created, updated or deleted) that the controller is watching, but also from different sources
(see event sources). When an event is received reconciliation is executed, unless there is already a reconciliation
happening for a particular custom resource. In other words it is guaranteed by the framework that no concurrent
reconciliation happens for a custom resource.

After a reconciliation (
i.e. [ResourceController](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/ResourceController.java)
called, a post-processing phase follows, where typically framework checks if:

- an exception was thrown during execution, if yes schedules a retry.
- there are new events received during the controller execution, if yes schedule the execution again.
- there is an instruction to re-schedule the execution for the future, if yes schedule a timer event with the specified
  delay.
- if none above, the reconciliation is finished.

Briefly, in the hearth of the execution is an eventing system, where events are the triggers of the reconciliation
execution.

## Finalizer Support

[Kubernetes finalizers](https://kubernetes.io/docs/concepts/overview/working-with-objects/finalizers/)
make sure that a reconciliation happens when a custom resource is instructed to be deleted. Typical case when it's
useful, when an operator is down (pod not running). Without a finalizer the reconciliation - thus the cleanup
i.e. [`Reconciler.cleanup(...)`](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/ResourceController.java)

- would not happen if a custom resource is deleted.

Finalizers are automatically added by the framework as the first step, thus when a custom resource is created, but
before the first reconciliation, the custom resource is updated via a Kubernetes API call. As a result of this update,
the finalizer will be present. The subsequent event will be received, which will trigger the first reconciliation.

The finalizer that is automatically added will be also removed after the `deleteResource` is executed on the
controllerConfiguration. However, the removal behavior can be further customized, and can be instructed to "not remove
yet" - this is useful just in some specific corner cases, when there would be a long waiting period for some dependent
resource cleanup.

The name of the finalizers can be specified, in case it is not, a name will be generated.

This behavior can be turned off, so when configured no finalizer will be added or removed.  
See [`@ControllerConfiguration`](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/ControllerConfiguration.java)
annotation for more details.

### When not to Use Finalizers?

Typically, automated finalizer handling should be turned off, in case **all** the cleanup of the dependent resources is
handled by Kubernetes itself. This is handled by
Kubernetes [garbage collection](https://kubernetes.io/docs/concepts/architecture/garbage-collection/#owners-dependents).
Setting the owner reference and related fields are not in the scope of the SDK for now, it's up to the user to have them
configured properly when creating the objects.

When automatic finalizer handling is turned off, the `ResourceController.deleteResource(...)` method is not called, in
case of a delete event received. So it does not make sense to implement this method and turn off finalizer at the same
time.

## The `reconcile` and `cleanup` Methods of `Reconciler`

The lifecycle of a custom resource can be clearly separated to two phases from a perspective of an operator. When a
custom resource is created or update, or on the other hand when the custom resource is deleted - or rater marked for
deletion in case a finalizer is used.

There is no point to make a distinction between create and update, since the reconciliation logic typically would be
very similar or identical in most of the cases.

This separation related logic is automatically handled by framework. The framework will always
call `createOrUpdateResource`
function, unless the custom resource is
[marked from deletion](https://kubernetes.io/docs/concepts/overview/working-with-objects/finalizers/#how-finalizers-work)
. From the point when the custom resource is marked from deletion, only the `deleteResource` method is called.

If there is **no finalizer** in place (see Finalizer Support section), the `deleteResource` method is **not called**.

### Using `UpdateControl` and `DeleteControl`

These two methods are used to control the outcome or the desired behavior after the reconciliation.

The `UpdateControl` can instruct the framework to update the custom resource status sub-resource and/or re-schedule a
reconciliation with a desired time delay. Those are the typical use cases, however in some cases there it can happen
that the controller wants to update the custom resource itself (like adding annotations) or not to do any updates, which
are also supported.

It is also possible to update both the status and the custom resource with `updateCustomResourceAndStatus` method. In
this case first the custom resource is updated then the status in two separate requests to K8S API.

Always update the custom resource with `UpdateControl`, not with the actual kubernetes client if possible.

On custom resource updates there is always an optimistic version control in place, to make sure that another update is
not overwritten (by setting `resourceVersion` ) .

The `DeleteControl` typically instructs the framework to remove the finalizer after the dependent resource are cleaned
up in `deleteResource` implementation.

However, there is a possibility to not remove the finalizer, this allows to clean up the resources in a more async way,
mostly for the cases when there is a long waiting period after a delete operation is initiated. Note that in this case
you might want to either schedule a timed event to make sure the
`deleteResource` is executed again or use event sources get notified about the state changes of a deleted resource.

## Automatic Retries on Error

When an exception is thrown from a controller, the framework will schedule an automatic retry of the reconciliation. The
retry is behavior is configurable, an implementation is provided that should cover most of the use-cases, see
[GenericRetry](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/retry/GenericRetry.java)
But it is possible to provide a custom implementation.

It is possible to set a limit on the number of retries. In
the [Context](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Context.java)
object information is provided about the retry, particularly interesting is the `isLastAttempt`, since a behavior could
be implemented bases on this flag. Like setting an error message in the status in case of a last attempt;

Event if the retry reached a limit, in case of a new event is received the reconciliation would happen again, it's just
won't be a result of a retry, but the new event.

A successful execution resets the retry.

### Setting Error Status After Last Retry Attempt

In order to facilitate error reporting in case a last retry attempt fails, Reconciler can implement the following
interface:

```java
public interface ErrorStatusHandler<T extends CustomResource<?, ?>> {

    T updateErrorStatus(T resource, RuntimeException e);

}
```

The `updateErrorStatus` resource is called when it's the last retry attempt according the retry configuration and the
reconciler execution still resulted in a runtime exception.

The result of the method call is used to make a status sub-resource update on the custom resource. This is always a
sub-resource update request, so no update on custom resource itself (like spec of metadata) happens. Note that this
update request will also produce an event, and will result in a reconciliation if the controller is not generation
aware.

Note that the scope of this feature is only the `reconcile` method of the reconciler, since there should not be updates
on custom resource after it is marked for deletion.

### Correctness and Automatic Retries

There is a possibility to turn of the automatic retries. This is not desirable, unless there is a very specific reason.
Errors naturally happen, typically network errors can cause some temporal issues, another case is when a custom resource
is updated during the reconciliation (using `kubectl` for example), in this case if an update of the custom resource
from the controller (using `UpdateControl`) would fail on a conflict. The automatic retries covers these cases and will
result in a reconciliation, even if normally an event would not be processed as a result of a custom resource update
from previous example (like if there is no generation update as a result of the change and generation filtering is
turned on)

## Rescheduling Execution

One way to implement an operator especially in simple cases is to periodically reconcile it. This is supported
explicitly by
`UpdateControl`, see method: `public UpdateControl<T> rescheduleAfter(long delay, TimeUnit timeUnit)`. This would
schedule a reconciliation for the future.

## Retry and Rescheduling and Unrelated Event Handling Common Behavior

Retry, reschedule and standard event processing forms a relatively complex system, where these functionalities are not
independent of each other. In the following we describe the behavior in this section, so it is easier to understand the
intersections:

1. A successful execution resets a retry and the rescheduled executions which were present before the reconciliation.
   However, a new rescheduling can be instructed from the reconciliation outcome (`UpdateControl` or `DeleteControl`).
2. In case an exception happened, and a retry is initiated, but an event received meanwhile the reconciliation will be
   executed instantly, and this execution won't count as a retry attempt.
3. If the retry limit is reached (so no more automatic retry would happen), but a new event received, the reconciliation
   will still happen, but won't reset the retry, will be still marked as the last attempt in the retry info. The point
    1. holds, but in case of an error, no retry will happen.

## Handling Related Events with Event Sources

See also this [blog post](https://csviri.medium.com/java-operator-sdk-introduction-to-event-sources-a1aab5af4b7b).

Event sources are a relatively simple yet powerful and extensible concept to trigger controller executions. Usually
based on changes of dependent resources. To solve the mentioned problems above, de-facto we watch resources we manage
for changes, and reconcile the state if a resource is changed. Note that resources we are watching can be Kubernetes and
also non-Kubernetes objects. Typically, in case of non-Kubernetes objects or services we can extend our operator to
handle webhooks or websockets or to react to any event coming from a service we interact with. What happens is when we
create a dependent resource we also register an Event Source that will propagate events regarding the changes of that
resource. This way we avoid the need of polling, and can implement controllers very efficiently.

![Alt text for broken image link](../assets/images/event-sources.png)

There are few interesting points here:
The CustomResourceEvenSource event source is a special one, which sends events regarding changes of our custom resource,
this is an event source which is always registered for every controller by default. An event is always related to a
custom resource. Concurrency is still handled for you, thus we still guarantee that there is no concurrent execution of
the controller for the same custom resource (
there is parallel execution if an event is related to another custom resource instance).

### Caching and Event Sources

Typically, when we work with Kubernetes (but possibly with others), we manage the objects in a declarative way. This is
true also for Event Sources. For example if we watch for changes of a Kubernetes Deployment object in the
InformerEventSource, we always receive the whole object from the Kubernetes API. Later when we try to reconcile in the
controller (not using events) we would like to check the state of this deployment (but also other dependent resources),
we could read the object again from Kubernetes API. However since we watch for the changes we know that we always
receive the most up-to-date version in the Event Source. So naturally, what we can do is cache the latest received
objects (in the Event Source) and read it from there if needed.

### Built-in Event Sources

1. InformerEventSource - used to get event about other K8S resources, also provides a local cache for them.
2. TimerEventSource - used to create timed events, mainly intended for internal usage.
3. CustomResourceEventSource - an event source that is automatically registered to listen to the changes of the main
   resource the operation manages, it also maintains a cache of those objects that can be accessed from the Reconciler.

## Monitoring with Micrometer

## Contextual Info for Logging with MDC

Logging is enhanced with additional contextual information using [MDC](http://www.slf4j.org/manual.html#mdc). This
following attributes are available in most parts of reconciliation logic and during the execution of the controller:

| MDC Key      | Value added from Custom Resource |
| :---        |    :---   | 
| `resource.apiVersion`   | `.apiVersion` |
| `resource.kind`   | `.kind` |
| `resource.name`      | `.metadata.name` | 
| `resource.namespace`   | `.metadata.namespace` |
| `resource.resourceVersion`   | `.metadata.resourceVersion` |
| `resource.generation`   | `.metadata.generation` |
| `resource.uid`   | `.metadata.uid` |

For more information about MDC see this [link](https://www.baeldung.com/mdc-in-log4j-2-logback).





