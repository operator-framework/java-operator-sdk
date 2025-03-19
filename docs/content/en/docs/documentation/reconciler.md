---
title: Implementing a reconciler
weight: 43
---

## Reconciliation Execution in a Nutshell

Reconciliation execution is always triggered by an event. Events typically come from a
primary resource, most of the time a custom resource, triggered by changes made to that resource
on the server (e.g. a resource is created, updated or deleted). Reconciler implementations are
associated with a given resource type and listens for such events from the Kubernetes API server
so that they can appropriately react to them. It is, however, possible for secondary sources to
trigger the reconciliation process. This usually occurs via
the [event source](#handling-related-events-with-event-sources) mechanism.

When an event is received reconciliation is executed, unless a reconciliation is already
underway for this particular resource. In other words, the framework guarantees that no
concurrent reconciliation happens for any given resource.

Once the reconciliation is done, the framework checks if:

- an exception was thrown during execution and if yes schedules a retry.
- new events were received during the controller execution, if yes schedule a new reconciliation.
- the reconcilier instructed the SDK to re-schedule a reconciliation at a later date, if yes
  schedules a timer event with the specified delay.
- none of the above, the reconciliation is finished.

In summary, the core of the SDK is implemented as an eventing system, where events trigger
reconciliation requests.

## Implementing a [`Reconciler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java) and/or [`Cleaner`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Cleaner.java)

The lifecycle of a Kubernetes resource can be clearly separated into two phases from the
perspective of an operator depending on whether a resource is created or updated, or on the
other hand if it is marked for deletion.

This separation-related logic is automatically handled by the framework. The framework will always
call the `reconcile` method, unless the custom resource is
[marked from deletion](https://kubernetes.io/docs/concepts/overview/working-with-objects/finalizers/#how-finalizers-work)
. On the other, if the resource is marked from deletion and if the `Reconciler` implements the
`Cleaner` interface, only the `cleanup` method will be called. Implementing the `Cleaner`
interface allows developers to let the SDK know that they are interested in cleaning related
state (e.g. out-of-cluster resources). The SDK will therefore automatically add a finalizer
associated with your `Reconciler` so that the Kubernetes server doesn't delete your resources
before your `Reconciler` gets a chance to clean things up.
See [Finalizer support](#finalizer-support) for more details.

### Using `UpdateControl` and `DeleteControl`

These two classes are used to control the outcome or the desired behaviour after the reconciliation.

The [`UpdateControl`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/UpdateControl.java)
can instruct the framework to update the status sub-resource of the resource
and/or re-schedule a reconciliation with a desired time delay:

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

Note, though, that using `EventSources` should be preferred to rescheduling since the
reconciliation will then be triggered only when needed instead than on a timely basis.

Those are the typical use cases of resource updates, however in some cases there it can happen that
the controller wants to update the resource itself (for example to add annotations) or not perform
any updates, which is also supported.

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
the cluster can automatically deleted them for you whenever the associated primary resource is
deleted. Note that setting owner references is the responsibility of the `Reconciler`
implementation, though [dependent resources](https://javaoperatorsdk.io/docs/dependent-resources)
make that process easier.

If you do need to clean such state, you need to use finalizers so that their
presence will prevent the Kubernetes server from deleting the resource before your operator is
ready to allow it. This allows for clean up to still occur even if your operator was down when
the resources was "deleted" by a user.

JOSDK makes cleaning resources in this fashion easier by taking care of managing finalizers
automatically for you when needed. The only thing you need to do is let the SDK know that your
operator is interested in cleaning state associated with your primary resources by having it
implement
the [`Cleaner<P>`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Cleaner.java)
interface. If your `Reconciler` doesn't implement the `Cleaner` interface, the SDK will consider
that you don't need to perform any clean-up when resources are deleted and will therefore not
activate finalizer support. In other words, finalizer support is added only if your `Reconciler`
implements the `Cleaner` interface.

Finalizers are automatically added by the framework as the first step, thus after a resource
is created, but before the first reconciliation. The finalizer is added via a separate
Kubernetes API call. As a result of this update, the finalizer will then be present on the
resource. The reconciliation can then proceed as normal.

The finalizer that is automatically added will be also removed after the `cleanup` is executed on
the reconciler. This behavior is customizable as explained
[above](#using-updatecontrol-and-deletecontrol) when we addressed the use of
`DeleteControl`.

You can specify the name of the finalizer to use for your `Reconciler` using the
[`@ControllerConfiguration`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java)
annotation. If you do not specify a finalizer name, one will be automatically generated for you.

From v5 by default finalizer is added using Served Side Apply. See also UpdateControl in docs.