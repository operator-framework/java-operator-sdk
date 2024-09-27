---
title: Version 5 Released! 
date: 2024-09-21
---

We are excited to announce that Java Operator SDK v5 has been released. This significant effort contains
various features and enhancements accumulated since the last major releases and required changes in our APIs.
Within this post, we will go through all the main changes and help you upgrade to this new version, and provide
a rationale behind the changes if necessary.

We will omit descriptions of changes that are trivial to update from the source code; feel free to contact
us if you have some trouble with updates.

## Various Changes 

- From this release, the minimal Java version is 17.
- Various deprecated APIs are removed. The migration should be trivial.

## Naming changes

TODO add handy diff links here

## Changes in low-level APIs

### Server Side Apply 

[Server Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/) is now a first-class citizen in the framework and
the default approach for patching the status resource. That means patching the resource or it's status through `UpdateControl` and adding
the finalizer in the background.

Migration from a non-SSA based patching to an SSA based one can be problematic. Make sure you test the transition when you migrate from older version of the frameworks. 
To continue to use a non-SSA based on, set [ConfigurationService.useSSAToPatchPrimaryResource](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L462) to `false`.

See some identified problematic migration cases and how to handle them in [StatusPatchSSAMigrationIT](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuspatchnonlocking/StatusPatchSSAMigrationIT.java).

TODO using new instance to update status always,

### Event Sources related changes

#### Multi-cluster support in InformerEventSource

`InformerEventSource` now supports watching remote clusters. You can simply pass an `KubernetesClient` that is
initialized to connect to a different cluster where the controller runs. See [InformerEventSourceConfiguration.withKubernetesClient](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/InformerEventSourceConfiguration.java)

Such an informer behaves exactly as a normal one. Obviously, owner references won't work, so you have to specify a `SecondaryToPrimaryMapper` (probably based on labels or annotations).

See related integration test [here](https://github.com/operator-framework/java-operator-sdk/tree/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/informerremotecluster)

#### SecondaryToPrimaryMapper now checks resource types

The owner reference based mappers are now checking the type (`kind` and `apiVersion`) of the resource when resolving the mapping. This is important
since a resource may have owner references to a different resource type with the same name.

See implementation details [here](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/Mappers.java#L74-L75)

#### InformerEventSource-related reactors

There are multiple smaller changes to `InformerEventSource` and related classes:

1. `InformerConfiguration` is renamed to [`InformerEventSourceConfiguration'](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/InformerEventSourceConfiguration.java)
2. `InformerEventSourceConfiguration` doesn't require `EventSourceContext` to be initialized anymore.
 
#### All EventSource is now a ResourceEventSource

The [`EventSource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java) abstraction is now always aware of the resources and
handles accessing (the cached) resources, filtering, and additional capabilities. Before v5, such capabilities were present only in a sub-class called `ResourceEventSource`,
but we decided to merge and remove `ResourceEventSource` since this has a nice impact on other parts of the system in terms of architecture. 

If you still need to create an `EventSource` that does only the triggering, see [TimerEventSource](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/timer/TimerEventSource.java) as an example. 

#### Naming event sources

The `name` is now directly property of the [`EventSource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java#L45).

This results in nicer internal structures. For example, if a DependentResource provides an EventSource, we have more options to set the name for it.

### @ControllerConfiguration is now optional

You no longer have to annotate the reconciler with `@ControllerConfiuraion` annotation. 
This annotation is (one) way to override the default properties of a controller.
If the annotation is not present, the default values from the annotation are used.

PR: https://github.com/operator-framework/java-operator-sdk/pull/2203

### EventSourceInitializer and ErrorStatusHandler are removed

Both the `EventSourceIntializer` and `ErrorStatusHandler` interfaces are removed, and their methods are moved directly 
under [`Reconciler`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java#L30-L56).

If possible, we try to avoid such marker interfaces since it is hard to deduce related usage just by looking at the source code. 
You can now simply override those methods when implementing the `Reconciler` interface.

### Cloning accessing secondary resources

When accessing the secondary resources using [`Context.getSecondaryResource(s)(...)`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Context.java#L19-L29), the resources are no longer cloned by default, since 
cloning could have an impact on performance. Note that means that these POJOs should be used only for "read-only"; any changes
are now made directly to the cached resource. This should be avoided since the same resource instance may be present for other reconciliation cycles and would
no longer represent the state on the server.

If you want to still clone resource by default, set [ConfigurationService.cloneSecondaryResourcesWhenGettingFromCache](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L484) to `true`.


### Remove automated observed generation handling

The automatic observed generation handling feature was removed since it is trivial to implement inside the reconciler, but it made
implementation much more complex, especially if the framework would have to support it both for served side apply and client side apply.

You can check a sample implementation how to do it manually in this [integration test](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/manualobservedgeneration/).

## Dependent Resource related changes

### ResourceDescriminator is removal and related topics



### Read-only bulk dependent resources

### Multiple Dependents with Activation Condition

## Workflow related Changes

### @Workflow annotation

### Explicit workflow invocation

### Silent exception handling

### CRDPresentActivationCondition 

## Additional minor changes

## Deprecation removals
