---
title: Version 5.0.0
weight: 10
---

# Version 5.0.0-RC1 Released!

We've just released the next major version of Java Operator SDK. There is not a single big feature or goal of this release. Instead, it is 
a collection of improvements and features that require API changes. However, the following improvements made the framework much easier to use and understand. 
In this post, we will review all the significant changes,
explain their rationale, and show how to migrate from the previous release if needed.

## Changes

### SSA for status patch and finalizer adding

[Server Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/) has already been supported for a long time
in the fabric8 client, and features like dependent resources. However, it is not used by default
to update the status of the custom resource with the `UpdateControl` patch operations in the reconciler and to
add the finalizer for the custom resource. 

If you do not wish to use SSA, you can deactivate the feature using `ConfigurationService.useSSAToPatchPrimaryResource` 
and related `ConfigurationServiceOverrider.withUseSSAToPatchPrimaryResource`.

It is essential to realize that migrating from a non-SSA to SSA-based resource handling has some issues in Kubernetes.
Therefore, the migration needs to be thoroughly tested.

See known issues with migration from non-SSA to SSA-based status updates here:
[integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L71-L82)
Also, the related [workaround](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L110-L116).

See [related issue](https://github.com/operator-framework/java-operator-sdk/issues/1931).

### Removal of automatic observed generation handling

The `ObservedGenerationAware` interface and `ObservedGenerationAwareStatus` and related functionality were removed.
Although this feature allowed us to handle observed generation in status easily, 
the rationale behind this removal is that it is not possible to implement it elegantly when using SSA. Also, 
it is trivial to implement it manually.

See the related integration test for how to do it manually [here](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/sample/manualobservedgeneration/ManualObservedGenerationReconciler.java).

See [related issue](https://github.com/operator-framework/java-operator-sdk/issues/2329).

### Removal of EventSourceInitializer interface

`EventSourceInitializer` is an interface that was implemented by most of the reconcilers. In general
we want to minimize such interfaces since it is hard to find them just "by looking at code", so this
interface was removed and `prepareEventSources(EventSourceContext<P> context)` was simply moved to
`Reconciler` interface with a default empty implementation.

So you can just delete this interface from your reconciler implementation.

```java

public class WebPageReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, E̶v̶e̶n̶t̶S̶o̶u̶r̶c̶e̶I̶n̶i̶t̶i̶a̶l̶i̶z̶e̶r̶<W̶e̶b̶P̶a̶g̶e̶> {

// omitted code

}

See [related issue](https://github.com/operator-framework/java-operator-sdk/issues/2029).

```

This interface also implemented some utility methods, which can now be found in [`EventSourceUtils`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/EventSourceUtils.java).

### Removal of ErrorStatusHandler interface

Similarly to `EventSourceInitializer` the `ErrorStatusHandler` interface is removed; now the `Reconciler` interface has a default method `updateErrorStatus`,
you can delete the interface from your `Reconciler`:

```java
public class WebPageReconciler
    implements Reconciler<WebPage>, E̶r̶r̶o̶r̶S̶t̶a̶t̶u̶s̶H̶a̶n̶d̶l̶e̶r̶<W̶e̶b̶P̶a̶g̶e̶> {

// omitted code

}
```

See [related issue](https://github.com/operator-framework/java-operator-sdk/issues/2437).

### Named event sources 

The name is now directly an attribute of an `EventSource`. EventSources were also named in previous releases, but the name was not an attribute. This leads mainly to better internal structures and 
solves issues out of the box. For example, when a dependent resource provides an event source, in some cases, it needs to have a specific name. In a `Reconciler` implementation,  
use a `List.of(event sources)` instead the `Map<String,EventSource>` as it was before:


```java

public class WebPageReconciler
    implements Reconciler<WebPage> {

// omitted code

  @Override
  public List<EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    var configMapEventSource =
        new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
            .withLabelSelector(SELECTOR)
            .build(), context);
    var deploymentEventSource = ... // code omitted 
    
    return List.of(configMapEventSource, deploymentEventSource);
  }

}
```

There is a new utility class [EventSourceUtils](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/EventSourceUtils.java#L10-L10) to help extract event source from workflows and 
dependent resources.









