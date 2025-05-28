---
title: Patterns and best practices
weight: 25
---

This document describes patterns and best practices, to build and run operators, and how to
implement them in terms of the Java Operator SDK (JOSDK).

See also best practices
in [Operator SDK](https://sdk.operatorframework.io/docs/best-practices/best-practices/).

## Implementing a Reconciler

### Reconcile All The Resources All the Time

The reconciliation can be triggered by events from multiple sources. It could be tempting to check
the events and reconcile just the related resource or subset of resources that the controller
manages. However, this is **considered an anti-pattern** for operators because the distributed
nature of Kubernetes makes it difficult to ensure that all events are always received. If, for
some reason, your operator doesn't receive some events, if you do not reconcile the whole state,
you might be operating with improper assumptions about the state of the cluster. This is why it
is important to always reconcile all the resources, no matter how tempting it might be to only
consider a subset. Luckily, JOSDK tries to make it as easy and efficient as possible by
providing smart caches to avoid unduly accessing the Kubernetes API server and by making sure
your reconciler is only triggered when needed.

Since there is a consensus regarding this topic in the industry, JOSDK does not provide
event access from `Reconciler` implementations anymore starting with version 2 of the framework.

### EventSources and Caching

As mentioned above during a reconciliation best practice is to reconcile all the dependent resources
managed by the controller. This means that we want to compare a desired state with the actual
state of the cluster. Reading the actual state of a resource from the Kubernetes API Server
directly all the time would mean a significant load. Therefore, it's a common practice to
instead create a watch for the dependent resources and cache their latest state. This is done
following the Informer pattern. In Java Operator SDK, informers are wrapped into an `EventSource`,
to integrate it with the eventing system of the framework. This is implemented by the
`InformerEventSource` class.

A new event that triggers the reconciliation is only propagated to the `Reconciler` when the actual
resource is already in cache. `Reconciler` implementations therefore only need to compare the
desired state with the observed one provided by the cached resource. If the resource cannot be
found in the cache, it therefore needs to be created. If the actual state doesn't match the
desired state, the resource needs to be updated.

### Idempotency

Since all resources should be reconciled when your `Reconciler` is triggered and reconciliations
can be triggered multiple times for any given resource, especially when retry policies are in
place, it is especially important that `Reconciler` implementations be idempotent, meaning that
the same observed state should result in exactly the same outcome. This also means that
operators should generally operate in stateless fashion. Luckily, since operators are usually
managing declarative resources, ensuring idempotency is usually not difficult.

### Sync or Async Way of Resource Handling

Depending on your use case, it's possible that your reconciliation logic needs to wait a
non-insignificant amount of time while the operator waits for resources to reach their desired
state. For example, you `Reconciler` might need to wait for a `Pod` to get ready before
performing additional actions. This problem can be approached either synchronously or
asynchronously.

The asynchronous way is to just exit the reconciliation logic as soon as the `Reconciler`
determines that it cannot complete its full logic at this point in time. This frees resources to
process other primary resource events. However, this requires that adequate event sources are
put in place to monitor state changes of all the resources the operator waits for. When this is
done properly, any state change will trigger the `Reconciler` again and it will get the
opportunity to finish its processing

The synchronous way would be to periodically poll the resources' state until they reach their
desired state. If this is done in the context of the `reconcile` method of your `Reconciler`
implementation, this would block the current thread for possibly a long time. It's therefore
usually recommended to use the asynchronous processing fashion.

## Why have Automatic Retries?

Automatic retries are in place by default and can be configured to your needs. It is also
possible to completely deactivate the feature, though we advise against it. The main reason
configure automatic retries for your `Reconciler` is due to the fact that errors occur quite
often due to the distributed nature of Kubernetes: transient network errors can be easily dealt
with by automatic retries. Similarly, resources can be modified by different actors at the same
time, so it's not unheard of to get conflicts when working with Kubernetes resources. Such
conflicts can usually be quite naturally resolved by reconciling the resource again. If it's
done automatically, the whole process can be completely transparent.

## Managing State

Thanks to the declarative nature of Kubernetes resources, operators that deal only with
Kubernetes resources can operate in a stateless fashion, i.e. they do not need to maintain
information about the state of these resources, as it should be possible to completely rebuild
the resource state from its representation (that's what declarative means, after all).
However, this usually doesn't hold true anymore when dealing with external resources, and it
might be necessary for the operator to keep track of this external state so that it is available
when another reconciliation occurs. While such state could be put in the primary resource's
status sub-resource, this could become quickly difficult to manage if a lot of state needs to be
tracked. It also goes against the best practice that a resource's status should represent the
actual resource state, when its spec represents the desired state. Putting state that doesn't
strictly represent the resource's actual state is therefore discouraged. Instead, it's
advised to put such state into a separate resource meant for this purpose such as a
Kubernetes Secret or ConfigMap or even a dedicated Custom Resource, which structure can be more
easily validated.

## Stopping (or not) Operator in case of Informer Errors and Cache Sync Timeouts

It can
be [configured](https://github.com/java-operator-sdk/java-operator-sdk/blob/2cb616c4c4fd0094ee6e3a0ef2a0ea82173372bf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L168-L168)
if the operator should stop in case of any informer error happens on startup. By default, if there ia an error on
startup and the informer for example has no permissions list the target resources (both the primary resource or
secondary resources) the operator will stop instantly. This behavior can be altered by setting the mentioned flag
to `false`, so operator will start even some informers are not started. In this case - same as in case when an informer
is started at first but experienced problems later - will continuously retry the connection indefinitely with an
exponential backoff. The operator will just stop if there is a fatal
error, [currently](https://github.com/java-operator-sdk/java-operator-sdk/blob/0e55c640bf8be418bc004e51a6ae2dcf7134c688/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerWrapper.java#L64-L66)
that is when a resource cannot be deserialized. The typical use case for changing this flag is when a list of namespaces
is watched by a controller. In is better to start up the operator, so it can handle other namespaces while there
might be a permission issue for some resources in another namespace.

The `stopOnInformerErrorDuringStartup` has implication on [cache sync timeout](https://github.com/java-operator-sdk/java-operator-sdk/blob/114c4312c32b34688811df8dd7cea275878c9e73/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L177-L179)
behavior. If true operator will stop on cache sync timeout. if `false`, after the timeout the controller will start
reconcile resources even if one or more event source caches did not sync yet.  

## Graceful Shutdown

You can provide sufficient time for the reconciler to process and complete the currently ongoing events before shutting down. 
The configuration is simple. You just need to set an appropriate duration value for `reconciliationTerminationTimeout` using `ConfigurationServiceOverrider`.

```java
final var overridden = new ConfigurationServiceOverrider(config)
    .withReconciliationTerminationTimeout(Duration.ofSeconds(5));

final var operator = new Operator(overridden);
```
