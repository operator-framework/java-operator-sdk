---
title: Version 5.0.0
weight: 10
---

# Version 5.0.0-RC1 Released!

We've just released the next major version of Java Operator SDK. There is no single goal for this release. It is rather
a collection of improvements and features that require API changes. In this post, we will go through the major changes,
explain their rationale, and show how to migrate from the previous release if needed.

## Changes

### Usage of SSA

[Server Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/) has already been supported for a long time
in the fabric8 client and features like dependent resources. However, it is not used by default
for updating the status of the custom resource with the `UpdateControl` patch operations in the reconciler and for
adding the finalizer for the custom resource. 

If you do not wish to use SSA, you can deactivate the feature using `ConfigurationService.useSSAToPatchPrimaryResource` 
and related `ConfigurationServiceOverrider.withUseSSAToPatchPrimaryResource`.

It is important to realize that migrating from a non-SSA to SSA-based resource handling has some issues in Kubernetes.
Therefore, the migration needs to be thoroughly tested.

See known issues with migration from non-SSA to SSA-based status updates here:
[integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L71-L82)
where it is demonstrated. Also, the related part of a [workaround](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L110-L116).



### Removal of EventSourceInitializer interface

`EventSourceInitializer` is an interface that was implemented by most of the reconcilers. In general
we want to minimize such interfaces since it is hard to find them just "by looking at code", so this
interface was removed and `prepareEventSources(EventSourceContext<P> context)` was simply moved to
`Reconciler` interface with a default empty implementation.

So you can simply just delete this interface from your reconciler implementation.

```java

public class WebPageReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, E̶v̶e̶n̶t̶S̶o̶u̶r̶c̶e̶I̶n̶i̶t̶i̶a̶l̶i̶z̶e̶r̶<W̶e̶b̶P̶a̶g̶e̶> {

// omitted code

}

see related issue [here](https://github.com/operator-framework/java-operator-sdk/issues/2029).

```

This interface implemented also some utility methods, now those cane be found in [`EventSourceUtils`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/EventSourceUtils.java).


### Named event sources 

The name is now directly an attribute of an `EventSource.` EventSources were also named in previous releases, but the name was not an attribute. This leads mainly to better internal structures also
solves issues out of the box. For example, when a dependent resource provides an event source, in some cases, it needs to have a specific name.

TODO






