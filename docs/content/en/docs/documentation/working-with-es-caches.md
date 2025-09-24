---
title: Working with EventSource caches
weight: 48
---

As described in [Event sources and related topics](eventing.md), event sources serve as the backbone
for caching resources and triggering reconciliation for primary resources that are related
to these secondary resources.

In the Kubernetes ecosystem, the component responsible for this is called an Informer. Without delving into
the details (there are plenty of excellent resources online about informers), informers
watch resources, cache them, and emit events when resources change.

`EventSource` is a generalized concept that extends the Informer pattern to non-Kubernetes resources,
allowing you to cache external resources and trigger reconciliation when those resources change.

## The InformerEventSource

The underlying informer implementation comes from the Fabric8 client, called [DefaultSharedIndexInformer](https://github.com/fabric8io/kubernetes-client/blob/main/kubernetes-client/src/main/java/io/fabric8/kubernetes/client/informers/impl/DefaultSharedIndexInformer.java).
[InformerEventSource](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerEventSource.java)
in Java Operator SDK wraps the Fabric8 client informers.
This wrapper adds additional capabilities specifically required for controllers
(note that informers have broader applications beyond just implementing controllers).

These additional capabilities include:
- Maintaining an index that maps secondary resources in the informer cache to their related primary resources
- Setting up multiple informers for the same resource type when needed (for example, you need one informer per namespace if the informer is not watching the entire cluster)
- Dynamically adding and removing watched namespaces
- Other capabilities that are beyond the scope of this document

### Associating Secondary Resources to Primary Resource

Event sources need to trigger the appropriate reconciler, providing the correct primary resource, whenever one of their
handled secondary resources changes. It is thus core to an event source's role to identify which primary resource (
usually, your custom resource) is potentially impacted by that change.
The framework uses [`SecondaryToPrimaryMapper`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/SecondaryToPrimaryMapper.java)
for this purpose. For `InformerEventSources`, which target Kubernetes resources, this mapping is typically done using
either the owner reference or an annotation on the secondary resource. For external resources, other mechanisms need to
be used and there are also cases where the default mechanisms provided by the SDK do not work, even for Kubernetes
resources.

However, once the event source has triggered a primary resource reconciliation, the associated reconciler needs to
access the secondary resources which changes caused the reconciliation. Indeed, the information from the secondary
resources might be needed during the reconciliation. For that purpose,  
`InformerEventSource` maintains a reverse
index [PrimaryToSecondaryIndex](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/DefaultPrimaryToSecondaryIndex.java),
based on the result of the `SecondaryToPrimaryMapper`result.

## Unified API for Related Resources

To access all related resources for a primary resource, the framework provides an API to access the related
secondary resources using the `Set<R> getSecondaryResources(Class<R> expectedType)` method of the `Context` object
provided as part of the `reconcile` method.

For `InformerEventSource`, this will leverage the associated `PrimaryToSecondaryIndex`. Resources are then retrieved
from the informer's cache. Note that since all those steps work
on top of indexes, those operations are very fast, usually O(1).

While we've focused mostly on `InformerEventSource`, this concept can be extended to all `EventSources`, since
[`EventSource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java#L93)
actually implements the `Set<R> getSecondaryResources(P primary)` method that can be called from the `Context`.

As there can be multiple event sources for the same resource types, things are a little more complex: the union of each
event source results is returned.

## Getting Resources Directly from Event Sources

Note that nothing prevents you from directly accessing resources in the cache without going through
`getSecondaryResources(...)`:

```java
public class WebPageReconciler implements Reconciler<WebPage> {

    InformerEventSource<ConfigMap, WebPage> configMapEventSource;

    @Override
    public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {
       // accessing resource directly from an event source 
       var mySecondaryResource = configMapEventSource.get(new ResourceID("name","namespace"));
       // details omitted
    }
    
    @Override
    public List<EventSource<?, WebPage>> prepareEventSources(EventSourceContext<WebPage> context) {
       configMapEventSource = new InformerEventSource<>(
                InformerEventSourceConfiguration.from(ConfigMap.class, WebPage.class)
                        .withLabelSelector(SELECTOR)
                        .build(),
                context);
        
        return List.of(configMapEventSource);
    }
}
```

## The Use Case for PrimaryToSecondaryMapper

**TL;DR**: `PrimaryToSecondaryMapper` allows `InformerEventSource` to access secondary resources directly
instead of using the PrimaryToSecondaryIndex. When this mapper is configured, `InformerEventSource.getSecondaryResources(..)`
will call the mapper to retrieve the target secondary resources. This is typically required when the `SecondaryToPrimaryMapper`
uses informer caches to list the target resources.

As discussed, we provide a unified API to access related resources using `Context.getSecondaryResources(...)`.
The term "Secondary" refers to resources that a reconciler needs to consider when properly reconciling a primary
resource. These resources encompass more than just "child" resources (resources created by a reconciler that
typically have an owner reference pointing to the primary custom resource). They also include
"related" resources (which may or may not be managed by Kubernetes) that serve as input for reconciliations.

In some cases, the SDK needs additional information beyond what's readily available, particularly when
secondary resources lack owner references or any direct link to their associated primary resource.

Consider this example: a `Job` primary resource can be assigned to run on a cluster, represented by a
`Cluster` resource.
Multiple jobs can run on the same cluster, so multiple `Job` resources can reference the same `Cluster` resource. However,
a `Cluster` resource shouldn't know about `Job` resources, as this information isn't part of what defines a cluster.
When a cluster changes, we might want to redirect associated jobs to other clusters. Our reconciler
therefore needs to determine which `Job` (primary) resources are associated with the changed `Cluster` (secondary)
resource.
See full
sample [here](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/primarytosecondary).

```java
InformerEventSourceConfiguration
        .from(Cluster.class, Job.class)
        .withSecondaryToPrimaryMapper(cluster -> context.getPrimaryCache()
              .list().filter(job -> job.getSpec().getClusterName().equals(cluster.getMetadata().getName()))
              .map(ResourceID::fromResource)
              .collect(Collectors.toSet()))
```

This configuration will trigger all related `Jobs` when the associated cluster changes and maintains the `PrimaryToSecondaryIndex`,
allowing us to use `getSecondaryResources` in the `Job` reconciler to access the cluster.
However, there's a potential issue: when a new `Job` is created, it doesn't automatically propagate
to the `PrimaryToSecondaryIndex` in the `Cluster`'s `InformerEventSource`. Re-indexing only occurs
when a `Cluster` event is received, which triggers all related `Jobs` again.
Until this re-indexing happens, you cannot use `getSecondaryResources` for the new `Job`, since it
won't be present in the reverse index.

You can work around this by accessing the Cluster directly from the cache in the reconciler:

```java 

@Override
public UpdateControl<Job> reconcile(Job resource, Context<Job> context) {

    clusterInformer.get(new ResourceID(job.getSpec().getClusterName(), job.getMetadata().getNamespace()));

    // omitted details
}
```

However, if you prefer to use the unified API (`context.getSecondaryResources()`), you need to add
a `PrimaryToSecondaryMapper`:

```java
clusterInformer.withPrimaryToSecondaryMapper( job -> 
        Set.of(new ResourceID(job.getSpec().getClusterName(), job.getMetadata().getNamespace())));
```

When using `PrimaryToSecondaryMapper`, the InformerEventSource bypasses the `PrimaryToSecondaryIndex`
and instead calls the mapper to retrieve resources based on its results.
In fact, when this mapper is configured, the `PrimaryToSecondaryIndex` isn't even initialized.

### Using Informer Indexes to Improve Performance

In the `SecondaryToPrimaryMapper` example above, we iterate through all resources in the cache:

```java
context.getPrimaryCache()
              .list().filter(job -> job.getSpec().getClusterName().equals(cluster.getMetadata().getName()))
```

This approach can be inefficient when dealing with a large number of primary (Job) resources. To improve performance,
you can create an index in the underlying Informer that indexes the target jobs for each cluster: 

```java

@Override
public List<EventSource<?, Job>> prepareEventSources(EventSourceContext<Job> context) {

    context.getPrimaryCache()
            .addIndexer(JOB_CLUSTER_INDEX,
                    (job -> List.of(indexKey(job.getSpec().getClusterName(), job.getMetadata().getNamespace()))));
    
    // omitted details
}
```

where `indexKey` is a String that uniquely identifies a Cluster:

```java
private String indexKey(String clusterName, String namespace) {
    return clusterName + "#" + namespace;
  }
```

With this index in place, you can retrieve the target resources very efficiently:

```java

  InformerEventSource<Job,Cluster> clusterInformer =
          new InformerEventSource(
        InformerEventSourceConfiguration.from(Cluster.class, Job.class)
            .withSecondaryToPrimaryMapper(
                cluster ->
                    context
                        .getPrimaryCache()
                        .byIndex(
                            JOB_CLUSTER_INDEX,
                            indexKey(
                                cluster.getMetadata().getName(),
                                cluster.getMetadata().getNamespace()))
                        .stream()
                        .map(ResourceID::fromResource)
                        .collect(Collectors.toSet()))
            .withNamespacesInheritedFromController().build(), context);
```