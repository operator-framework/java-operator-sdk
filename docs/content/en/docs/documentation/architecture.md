---
title: Architecture and Internals
weight: 85
---

This document provides an overview of the Java Operator SDK's internal structure and components to help developers understand and contribute to the project. While not a comprehensive reference, it introduces core concepts that should make other components easier to understand.

## The Big Picture and Core Components

![JOSDK architecture](/images/architecture.svg)

An [Operator](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/Operator.java) is a set of independent [controllers](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/Controller.java).

The `Controller` class is an internal class managed by the framework and typically shouldn't be interacted with directly. It manages all processing units involved with reconciling a single type of Kubernetes resource.

### Core Components

- **[Reconciler](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java)** - The primary entry point for developers to implement reconciliation logic
- **[EventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java)** - Represents a source of events that might trigger reconciliation
- **[EventSourceManager](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/EventSourceManager.java)** - Aggregates all event sources for a controller and manages their lifecycle
- **[ControllerResourceEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/controller/ControllerResourceEventSource.java)** - Central event source that watches primary resources associated with a given controller for changes, propagates events and caches state
- **[EventProcessor](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/EventProcessor.java)** - Processes incoming events sequentially per resource while allowing concurrent overall processing. Handles rescheduling and retrying
- **[ReconcilerDispatcher](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/ReconciliationDispatcher.java)** - Dispatches requests to appropriate `Reconciler` methods and handles reconciliation results, making necessary Kubernetes API calls

## Typical Workflow

A typical workflow follows these steps:

1. **Event Generation**: An `EventSource` produces an event and propagates it to the `EventProcessor`
2. **Resource Reading**: The resource associated with the event is read from the internal cache
3. **Reconciliation Submission**: If the resource isn't already being processed, a reconciliation request is submitted to the executor service in a different thread (encapsulated in a `ControllerExecution` instance)
4. **Dispatching**: The `ReconcilerDispatcher` is called, which dispatches the call to the appropriate `Reconciler` method with all required information
5. **Reconciler Execution**: Once the `Reconciler` completes, the `ReconcilerDispatcher` makes appropriate Kubernetes API server calls based on the returned result
6. **Finalization**: The `EventProcessor` is called back to finalize execution and update the controller's state
7. **Rescheduling Check**: The `EventProcessor` checks if the request needs rescheduling or retrying, and whether subsequent events were received for the same resource
8. **Completion**: When no further action is needed, event processing is finished 
