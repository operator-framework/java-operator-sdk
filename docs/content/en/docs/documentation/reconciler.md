---
title: Implementing a reconciler
weight: 45
---

## How Reconciliation Works

The reconciliation process is event-driven and follows this flow:

1. **Event Reception**: Events trigger reconciliation from:
   - **Primary resources** (usually custom resources) when created, updated, or deleted
   - **Secondary resources** through registered event sources

2. **Reconciliation Execution**: Each reconciler handles a specific resource type and listens for events from the Kubernetes API server. When an event arrives, it triggers reconciliation unless one is already running for that resource. The framework ensures no concurrent reconciliation occurs for the same resource.

3. **Post-Reconciliation Processing**: After reconciliation completes, the framework:
   - Schedules a retry if an exception was thrown
   - Schedules new reconciliation if events were received during execution  
   - Schedules a timer event if rescheduling was requested (`UpdateControl.rescheduleAfter(..)`)
   - Finishes reconciliation if none of the above apply

The SDK core implements an event-driven system where events trigger reconciliation requests.

## Implementing Reconciler and Cleaner Interfaces

To implement a reconciler, you must implement the [`Reconciler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java) interface.

A Kubernetes resource lifecycle has two phases depending on whether the resource is marked for deletion:

**Normal Phase**: The framework calls the `reconcile` method for regular resource operations.

**Deletion Phase**: If the resource is marked for deletion and your `Reconciler` implements the [`Cleaner`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Cleaner.java) interface, only the `cleanup` method is called. The framework automatically handles finalizers for you.

If you need explicit cleanup logic, always use finalizers. See [Finalizer support](#finalizer-support) for details.

### Using `UpdateControl` and `DeleteControl`

These classes control the behavior after reconciliation completes.

[`UpdateControl`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/UpdateControl.java) can instruct the framework to:
- Update the status sub-resource
- Reschedule reconciliation with a time delay

```java 
  @Override
  public UpdateControl<MyCustomResource> reconcile(
     EventSourceTestCustomResource resource, Context context) {
    // omitted code
    
    return UpdateControl.patchStatus(resource).rescheduleAfter(10, TimeUnit.SECONDS);
  }
```

without an update:

```java 
  @Override
  public UpdateControl<MyCustomResource> reconcile(
     EventSourceTestCustomResource resource, Context context) {
    // omitted code
    
    return UpdateControl.<MyCustomResource>noUpdate().rescheduleAfter(10, TimeUnit.SECONDS);
  }
```

Note, though, that using `EventSources` is the preferred way of scheduling since the
reconciliation is triggered only when a resource is changed, not on a timely basis.

At the end of the reconciliation, you typically update the status sub-resources. 
It is also possible to update both the status and the resource with the `patchResourceAndStatus` method. In this case,
the resource is updated first followed by the status, using two separate requests to the Kubernetes API.

From v5 `UpdateControl` only supports patching the resources, by default
using [Server Side Apply (SSA)](https://kubernetes.io/docs/reference/using-api/server-side-apply/).
It is important to understand how SSA works in Kubernetes. Mainly, resources applied using SSA
should contain only the fields identifying the resource and those the user is interested in (a 'fully specified intent'
in Kubernetes parlance), thus usually using a resource created from scratch, see
[sample](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/patchresourcewithssa).
To contrast, see the same sample, this time [without SSA](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/patchresourceandstatusnossa/PatchResourceAndStatusNoSSAReconciler.java).

Non-SSA based patch is still supported.  
You can control whether or not to use SSA
using [`ConfigurationServcice.useSSAToPatchPrimaryResource()`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L385-L385)
and the related `ConfigurationServiceOverrider.withUseSSAToPatchPrimaryResource` method.
Related integration test can be
found [here](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/patchresourceandstatusnossa).

Handling resources directly using the client, instead of delegating these updates operations to JOSDK by returning
an `UpdateControl` at the end of your reconciliation, should work appropriately. However, we do recommend to
use `UpdateControl` instead since JOSDK makes sure that the operations are handled properly, since there are subtleties
to be aware of. For example, if you are using a finalizer, JOSDK makes sure to include it in your fully specified intent
so that it is not unintentionally removed from the resource (which would happen if you omit it, since your controller is
the designated manager for that field and Kubernetes interprets the finalizer being gone from the specified intent as a
request for removal).

[`DeleteControl`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/DeleteControl.java)
typically instructs the framework to remove the finalizer after the dependent
resource are cleaned up in `cleanup` implementation.

```java

public DeleteControl cleanup(MyCustomResource customResource,Context context){
        // omitted code
    
        return DeleteControl.defaultDelete();
        }

```

However, it is possible to instruct the SDK to not remove the finalizer, this allows to clean up
the resources in a more asynchronous way, mostly for cases when there is a long waiting period
after a delete operation is initiated. Note that in this case you might want to either schedule
a timed event to make sure `cleanup` is executed again or use event sources to get notified
about the state changes of the deleted resource.

### Finalizer Support

[Kubernetes finalizers](https://kubernetes.io/docs/concepts/overview/working-with-objects/finalizers/)
make sure that your `Reconciler` gets a chance to act before a resource is actually deleted
after it's been marked for deletion. Without finalizers, the resource would be deleted directly
by the Kubernetes server.

Depending on your use case, you might or might not need to use finalizers. In particular, if
your operator doesn't need to clean any state that would not be automatically managed by the
Kubernetes cluster (e.g. external resources), you might not need to use finalizers. You should
use the
Kubernetes [garbage collection](https://kubernetes.io/docs/concepts/architecture/garbage-collection/#owners-dependents)
mechanism as much as possible by setting owner references for your secondary resources so that
the cluster can automatically delete them for you whenever the associated primary resource is
deleted. Note that setting owner references is the responsibility of the `Reconciler`
implementation, though [dependent resources](https://javaoperatorsdk.io/docs/documentation/dependent-resource-and-workflows/dependent-resources/)
make that process easier.

If you do need to clean such a state, you need to use finalizers so that their
presence will prevent the Kubernetes server from deleting the resource before your operator is
ready to allow it. This allows for clean-up even if your operator was down when the resource was marked for deletion.

JOSDK makes cleaning resources in this fashion easier by taking care of managing finalizers
automatically for you when needed. The only thing you need to do is let the SDK know that your
operator is interested in cleaning the state associated with your primary resources by having it
implement
the [`Cleaner<P>`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Cleaner.java)
interface. If your `Reconciler` doesn't implement the `Cleaner` interface, the SDK will consider
that you don't need to perform any clean-up when resources are deleted and will, therefore, not activate finalizer support.
In other words, finalizer support is added only if your `Reconciler` implements the `Cleaner` interface.

The framework automatically adds finalizers as the first step, thus after a resource
is created but before the first reconciliation. The finalizer is added via a separate
Kubernetes API call. As a result of this update, the finalizer will then be present on the
resource. The reconciliation can then proceed as normal.

The automatically added finalizer will also be removed after the `cleanup` is executed on
the reconciler. This behavior is customizable as explained
[above](#using-updatecontrol-and-deletecontrol) when we addressed the use of
`DeleteControl`.

You can specify the name of the finalizer to use for your `Reconciler` using the
[`@ControllerConfiguration`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java)
annotation. If you do not specify a finalizer name, one will be automatically generated for you.

From v5, by default, the finalizer is added using Server Side Apply. See also `UpdateControl` in docs.

### Making sure the primary resource is up to date for the next reconciliation

It is typical to want to update the status subresource with the information that is available during the reconciliation.
This is sometimes referred to as the last observed state. When the primary resource is updated, though, the framework
does not cache the resource directly, relying instead on the propagation of the update to the underlying informer's
cache. It can, therefore, happen that, if other events trigger other reconciliations, before the informer cache gets
updated, your reconciler does not see the latest version of the primary resource. While this might not typically be a
problem in most cases, as caches eventually become consistent, depending on your reconciliation logic, you might still
require the latest status version possible, for example, if the status subresource is used to store allocated values.
See [Representing Allocated Values](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#representing-allocated-values)
from the Kubernetes docs for more details.

The framework provides the  
[`PrimaryUpdateAndCacheUtils`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/PrimaryUpdateAndCacheUtils.java) utility class
to help with these use cases.

This class' methods use internal caches in combination with update methods that leveraging 
optimistic locking. If the update method fails on optimistic locking, it will retry 
using a fresh resource from the server as base for modification. 

```java
@Override
public UpdateControl<StatusPatchCacheCustomResource> reconcile(
        StatusPatchCacheCustomResource resource, Context<StatusPatchCacheCustomResource> context) {
    
    // omitted logic
    
    // update with SSA requires a fresh copy
    var freshCopy = createFreshCopy(primary);
    freshCopy.getStatus().setValue(statusWithState());
    
    var updatedResource = PrimaryUpdateAndCacheUtils.ssaPatchStatusAndCacheResource(resource, freshCopy, context);

    // the resource was updated transparently via the utils, no further action is required via UpdateControl in this case
    return UpdateControl.noUpdate();
  }
```

After the update `PrimaryUpdateAndCacheUtils.ssaPatchStatusAndCacheResource` puts the result of the update into an internal
cache and the framework will make sure that the next reconciliation contains the most recent version of the resource.
Note that it is not necessarily the same version returned as response from the update, it can be a newer version since other parties
can do additional updates meanwhile. However, unless it has been explicitly modified, that
resource will contain the up-to-date status.

Note that you can also perform additional updates after the `PrimaryUpdateAndCacheUtils.*PatchStatusAndCacheResource` is
called, either by calling any of the `PrimeUpdateAndCacheUtils` methods again or via `UpdateControl`. Using
`PrimaryUpdateAndCacheUtils` guarantees that the next reconciliation will see a resource state no older than the version
updated via `PrimaryUpdateAndCacheUtils`.

See related integration test [here](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuscache).

### Trigger reconciliation on all events 

TLDR; We provide an execution mode where `reconcile` method is called on every event from event source.

The framework optimizes execution for generic use cases, which in almost all cases falls into two categories:

1. The controller does not use finalizers; thus when the primary resource is deleted, all the managed secondary 
   resources are cleaned up using the Kubernetes garbage collection mechanism, a.k.a., using owner references.
2. When finalizers are used (using `Cleaner` interface), thus when controller requires some explicit cleanup logic, typically for external 
   resources and when secondary resources are in different namespace than the primary resources (owner references
   cannot be used in this case).

Note that for example framework neither of those cases triggers the reconciler on the `Delete` event of the primary resource.
When finalizer is used, it calls `cleaner(..)` method when resource is marked for deletion and our (not other) finalizer
is present. When there is no finalizer, does not make sense to call the `reconciel(..)` method on a `Delete` event
since all the cleanup will be done by the garbage collector. This way we spare reconciliation cycles.

However, there are cases when controllers do not strictly follow those patterns, typically when:
- Only some of the primary resources use finalizers, e.g., for some of the primary resources you need 
  to create an external resource for others not.
- You maintain some additional in memory caches (so not all the caches are encapsulated by an `EventSource`)
  and you don't want to use finalizers. For those cases, you typically want to clean up your caches when the primary
  resource is deleted.

For such use cases you can set [`triggerReconcilerOnAllEvent`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java#L81)
to `true`, as a result, `reconcile` method will be triggered on ALL events (so also `Delete` events), and you
are free to optimize you reconciliation for the use cases above and possibly others. 

In this mode:
- you cannot use `Cleaner` interface. The framework assumes you will explicitly manage the finalizers. To 
  add finalizer you can use [`PrimeUpdateAndCacheUtils`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/PrimaryUpdateAndCacheUtils.java#L308).
- even if the primary resource is already deleted from the Informer's cache, we will still pass the last known state
  as the parameter for the reconciler. You can check if the resource is deleted using `Context.isPrimaryResourceDeleted()`.
- The retry, rate limiting, re-schedule mechanisms work normally. (The internal caches related to the resource 
  are cleaned up only when there was a successful reconiliation after `Delete` event received for the primary resource
  and reconciliation was not re-scheduled.
- you cannot use managed dependent resources since those manage the finalizers and other logic related to the normal
  execution mode.
  