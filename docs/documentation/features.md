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
i.e. [`ResourceController.deleteResource(...)`](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/ResourceController.java)

- would not happen if a custom resource is deleted.

Finalizers are automatically added by the framework as the first step, thus when a custom resource is created, but
before the first reconciliation, the custom resource is updated via a Kubernetes API call. As a result of this update, the
finalizer will be present. The subsequent event will be received, which will trigger the first reconciliation.

The finalizer that is automatically added will be also removed after the `deleteResource` is executed on the controller.
However, the removal behavior can be further customized, and can be instructed to "not remove yet" - this is useful just
in some specific corner cases, when there would be a long waiting period for some dependent resource cleanup.

The name of the finalizers can be specified, in case it is not, a name will be generated.

This behavior can be turned off, so when configured no finalizer will be added or removed.  
See [`@Controller`](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Controller.java)
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

## The `createOrUpdateResource` and `deleteResource` Methods of `ResourceController`

The lifecycle of a custom resource can be clearly separated to two phases from a perspective of an operator. 
When a custom resource is created or update, or on the other hand when the custom resource is deleted - or rater 
marked for deletion in case a finalizer is used. 

There is no point to make a distinction between create and update, since the reconciliation 
logic typically would be very similar or identical in most of the cases. 

This separation related logic is automatically handled by framework. The framework will always call `createOrUpdateResource`
function, unless the custom resource is 
[marked from deletion](https://kubernetes.io/docs/concepts/overview/working-with-objects/finalizers/#how-finalizers-work). 
From the point when the custom resource is marked from deletion, only the `deleteResource` method is called. 

If there is **no finalizer** in place (see Finalizer Support section), the `deleteResource` method is **not called**.

### Using `UpdateControl` and `DeleteControl`

These two methods are used to control the outcome or the desired behavior after the reconciliation. 

The `UpdateControl` can instruct the framework to update the custom resource status sub-resource and/or re-schedule 
a reconciliation with a desired time delay. Those are the typical use cases, however in some cases there it can happen
that the controller wants to update the custom resource itself (like adding annotations) or not to do any updates, 
which are also supported.

It is also possible to update both the status and the custom resource with `updateCustomResourceAndStatus` method. 
In this case first the custom resource is updated then the status in two separate requests to K8S API.

Always update the custom resource with `UpdateControl`, not with the actual kubernetes client if possible.

On custom resource updates there is always an optimistic version control in place, to make sure that another update is
not overwritten (by setting `resourceVersion` ) . 

The `DeleteControl` typically instructs the framework to remove the finalizer after the dependent resource are 
cleaned up in `deleteResource` implementation. 

However, there is a possibility to not remove the finalizer, this 
allows to clean up the resources in a more async way, mostly for the cases when there is a long waiting period after a delete 
operation is initiated. Note that in this case you might want to either schedule a timed event to make sure the 
`deleteResource` is executed again or use event sources get notified about the state changes of a deleted resource. 

## Automatic Retries on Error

When an exception is thrown from the controller, the framework will do an automatic retry of the reconciliation.
The retry is behavior is configurable, an implementation is provided that should cover most of the use-cases, see 
[GenericRetry](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/retry/GenericRetry.java)
But it is possible to provide a custom implementation.

It is possible to set a limit on the number of retries. In the [Context](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Context.java)
object information is provided about the retry, particularly interesting is the `isLastAttempt`, since a behavior
could be implemented in case this is the last attempt, like setting an error message into status of the resource. 

Event if the retry reached a limit, in case of a new event is received the reconciliation would happen again, it's 
just won't be a result of a retry, but the new event. 

A successful execution resets the retry.

### Correctness and automatic retry

There is a possibility to turn of the automatic retries. This is not desirable, unless there some very specific
reason. Errors naturally happen, typically network errors can cause some temporal issues, but 
another case is when a custom resource is updated during the reconciliation (using `kubectl` for example), in this case
if an update of the custom resource from the controller (using `UpdateControl`) would fail on a conflict. The automatic
retries covers these cases and will result in a reconciliation. Even if normally an event would not be processed 
as a result of a custom resource update from previous example (like if there is no generation update as a result of the 
change and generation filtering is turned on) 

## Re-Scheduling Execution

In simple operators one way to implement an operator is to periodically reconcile it. This is supported explicitly by
`UpdateControl`, see method: `public UpdateControl<T> withReSchedule(long delay, TimeUnit timeUnit)`. 
This would schedule a reconciliation to the future.

## Retry and Re-Scheduling Common Behavior


## Handling Related Events with Event Sources

### Caching and Event Sources

### The CustomResourceEventSource

### Built-in Event Sources

## Monitoring with Micrometer

## Advanced Behavior




