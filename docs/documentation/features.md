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

Typically, automated finalizer handling should be turned off, when **all** the cleanup of the dependent resources is
handled by Kubernetes itself. This is handled by
Kubernetes [garbage collection](https://kubernetes.io/docs/concepts/architecture/garbage-collection/#owners-dependents).
Setting the owner reference and related fields are not in the scope of the SDK for now, it's up to the user to have them
configured properly when creating the objects.

When automatic finalizer handling is turned off, the `ResourceController.deleteResource(...)` method is not called, in
case of a delete event received. So it does not make sense to implement this method and turn off finalizer at the same
time.

## Separating `createOrUpdate` from `delete`

## Automatic Retries on Error

### Correctness and automatic retry

## Re-Scheduling Execution

## Retry and Re-Scheduling Common Behavior

## Handling Related Events with Event Sources

### Caching and Event Sources

### The CustomResourceEventSource

### Built-in Event Sources

## Monitoring with Micrometer





