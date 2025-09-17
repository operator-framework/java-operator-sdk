---
title: Patterns and best practices
weight: 25
---

This document describes patterns and best practices for building and running operators, and how to implement them using the Java Operator SDK (JOSDK).

See also best practices in the [Operator SDK](https://sdk.operatorframework.io/docs/best-practices/best-practices/).

## Implementing a Reconciler

### Always Reconcile All Resources

Reconciliation can be triggered by events from multiple sources. It might be tempting to check the events and only reconcile the related resource or subset of resources that the controller manages. However, this is **considered an anti-pattern** for operators.

**Why this is problematic:**
- Kubernetes' distributed nature makes it difficult to ensure all events are received
- If your operator misses some events and doesn't reconcile the complete state, it might operate with incorrect assumptions about the cluster state
- Always reconcile all resources, regardless of the triggering event

JOSDK makes this efficient by providing smart caches to avoid unnecessary Kubernetes API server access and ensuring your reconciler is triggered only when needed.

Since there's industry consensus on this topic, JOSDK no longer provides event access from `Reconciler` implementations starting with version 2.

### Event Sources and Caching

During reconciliation, best practice is to reconcile all dependent resources managed by the controller. This means comparing the desired state with the actual cluster state. 

**The Challenge**: Reading the actual state directly from the Kubernetes API Server every time would create significant load.

**The Solution**: Create a watch for dependent resources and cache their latest state using the Informer pattern. In JOSDK, informers are wrapped into `EventSource` to integrate with the framework's eventing system via the `InformerEventSource` class.

**How it works**:
- New events trigger reconciliation only when the resource is already cached
- Reconciler implementations compare desired state with cached observed state
- If a resource isn't in cache, it needs to be created
- If actual state doesn't match desired state, the resource needs updating

### Idempotency

Since all resources should be reconciled when your `Reconciler` is triggered, and reconciliations can be triggered multiple times for any given resource (especially with retry policies), it's crucial that `Reconciler` implementations be **idempotent**.

**Idempotency means**: The same observed state should always result in exactly the same outcome.

**Key implications**:
- Operators should generally operate in a stateless fashion
- Since operators usually manage declarative resources, ensuring idempotency is typically straightforward

### Synchronous vs Asynchronous Resource Handling

Sometimes your reconciliation logic needs to wait for resources to reach their desired state (e.g., waiting for a `Pod` to become ready). You can approach this either synchronously or asynchronously.

#### Asynchronous Approach (Recommended)

Exit the reconciliation logic as soon as the `Reconciler` determines it cannot complete at this point. This frees resources to process other events.

**Requirements**: Set up adequate event sources to monitor state changes of all resources the operator waits for. When state changes occur, the `Reconciler` is triggered again and can finish processing.

#### Synchronous Approach

Periodically poll resources' state until they reach the desired state. If done within the `reconcile` method, this blocks the current thread for potentially long periods.

**Recommendation**: Use the asynchronous approach for better resource utilization.

## Why Use Automatic Retries?

Automatic retries are enabled by default and configurable. While you can deactivate this feature, we advise against it.

**Why retries are important**:
- **Transient network errors**: Common in Kubernetes' distributed environment, easily resolved with retries
- **Resource conflicts**: When multiple actors modify resources simultaneously, conflicts can be resolved by reconciling again
- **Transparency**: Automatic retries make error handling completely transparent when successful

## Managing State

Thanks to Kubernetes resources' declarative nature, operators dealing only with Kubernetes resources can operate statelessly. They don't need to maintain resource state information since it should be possible to rebuild the complete resource state from its representation.

### When State Management Becomes Necessary

This stateless approach typically breaks down when dealing with external resources. You might need to track external state or allocated 
values for future reconciliations. There are multiple options:


1. Putting state in the primary resource's status sub-resource. This is a bit more complex that might seem at the first look.
   Refer to the [documentation](../documentation/reconciler.md#making-sure-the-primary-resource-is-up-to-date-for-the-next-reconciliation)
   for further details.

2. Store state in separate resources designed for this purpose:
- Kubernetes Secret or ConfigMap
- Dedicated Custom Resource with validated structure

## Handling Informer Errors and Cache Sync Timeouts

You can [configure](https://github.com/java-operator-sdk/java-operator-sdk/blob/2cb616c4c4fd0094ee6e3a0ef2a0ea82173372bf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L168-L168) whether the operator should stop when informer errors occur on startup.

### Default Behavior
By default, if there's a startup error (e.g., the informer lacks permissions to list target resources for primary or secondary resources), the operator stops immediately.

### Alternative Configuration  
Set the flag to `false` to start the operator even when some informers fail to start. In this case:
- The operator continuously retries connection with exponential backoff
- This applies both to startup failures and runtime problems
- The operator only stops for fatal errors (currently when a resource cannot be deserialized)

**Use case**: When watching multiple namespaces, it's better to start the operator so it can handle other namespaces while resolving permission issues in specific namespaces.

### Cache Sync Timeout Impact
The `stopOnInformerErrorDuringStartup` setting affects [cache sync timeout](https://github.com/java-operator-sdk/java-operator-sdk/blob/114c4312c32b34688811df8dd7cea275878c9e73/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L177-L179) behavior:
- **If `true`**: Operator stops on cache sync timeout
- **If `false`**: After timeout, the controller starts reconciling resources even if some event source caches haven't synced yet  

## Graceful Shutdown

You can provide sufficient time for the reconciler to process and complete ongoing events before shutting down. Simply set an appropriate duration value for `reconciliationTerminationTimeout` using `ConfigurationServiceOverrider`.

```java
final var operator = new Operator(override -> override.withReconciliationTerminationTimeout(Duration.ofSeconds(5)));
```
