---
title: Architecture and Internals
weight: 85
---

This document gives an overview of the internal structure and components of Java Operator SDK core,
in order to make it easier for developers to understand and contribute to it. This document is
not intended to be a comprehensive reference, rather an introduction to the core concepts and we
hope that the other parts should be fairly easy to understand. We will evolve this document
based on the community's feedback.

## The Big Picture and Core Components

![JOSDK architecture](/images/architecture.svg)

An [Operator](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/Operator.java)
is a set of
independent [controllers](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/Controller.java)
.
The `Controller` class, however, is an internal class managed by the framework itself and
usually shouldn't interacted with directly by end users. It
manages all the processing units involved with reconciling a single type of Kubernetes resource.

Other components include:

- [Reconciler](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java)
  is the primary entry-point for the developers of the framework to implement the reconciliation
  logic.
- [EventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java)
  represents a source of events that might eventually trigger a reconciliation.
- [EventSourceManager](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/EventSourceManager.java)
  aggregates all the event sources associated with a controller. Manages the event sources'
  lifecycle.
- [ControllerResourceEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/controller/ControllerResourceEventSource.java)
  is a central event source that watches the resources associated with the controller (also
  called primary resources) for changes, propagates events and caches the related state.
- [EventProcessor](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/EventProcessor.java)
  processes the incoming events and makes sure they are executed in a sequential manner, that is
  making sure that the events are processed in the order they are received for a given resource,
  despite requests being processed concurrently overall. The `EventProcessor` also takes care of
  re-scheduling or retrying requests as needed.
- [ReconcilerDispatcher](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/ReconciliationDispatcher.java)
  is responsible for dispatching requests to the appropriate `Reconciler` method and handling
  the reconciliation results, making the instructed Kubernetes API calls.

## Typical Workflow

A typical workflows looks like following:

1. An `EventSource` produces an event, that is propagated to the `EventProcessor`.
2. The resource associated with the event is read from the internal cache.
3. If the resource is not already being processed, a reconciliation request is
   submitted to the executor service to be executed in a different thread, encapsulated in a
   `ControllerExecution` instance.
4. This, in turns, calls the `ReconcilerDispatcher` which dispatches the call to the appropriate
   `Reconciler` method, passing along all the required information.
5. Once the `Reconciler` is done, what happens depends on the result returned by the
   `Reconciler`. If needed, the `ReconcilerDispatcher` will make the appropriate calls to the
   Kubernetes API server.
6. Once the `Reconciler` is done, the `EventProcessor` is called back to finalize the
   execution and update the controller's state.
7. The `EventProcessor` checks if the request needs to be rescheduled or retried and if there are no
   subsequent events received for the same resource.
8. When none of this happens, the processing of the event is finished. 
