---
title: Accessing resources in caches
weight: 48
---

As described in [Event sources and related topics](eventing.md) event sources are the backbone
for caching resources and triggering the reconciliation for primary resources thar are related 
to cached resources.

In Kubernetes world, the component that does this is called Informer. Without going into
the details (there are plenty of good documents online regarding informers), its responsibility
is to watch resources, cache them, and emit an event if the resource changed.

EventSource is a generalized concept of Informer to non-Kubernetes resources. Thus,
to cache external resources, and trigger reconciliation if those change.

## The InformerEventSource

The underlying informer implementation comes from the fabric8 client, called [DefaultSharedIndexInformer](https://github.com/fabric8io/kubernetes-client/blob/main/kubernetes-client/src/main/java/io/fabric8/kubernetes/client/informers/impl/DefaultSharedIndexInformer.java).
[InformerEventSource](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerEventSource.java) 
in Java Operator SDK wraps informers from fabric8 client.
The purpose of such wrapping is to add additional capabilities required for controllers.
(In general, Informers are not used only for implementing controllers).

Such capabilities are:
- maintaining and index to which primary are the secondary resources in informer cache are related to.
- setting up multiple informers for the same type if needed. You need informer per namespace if the informer 
  is not watching the whole cluster.
- Dynamically adding/removing watched namespaces.
- Some others, what is out of the scope of this document.

### Associating Secondary Resources to Primary Resource

The question is, how to trigger reconciliation of a primary resources (your custom resource),
when Informer receives a new resource.
For this purpose the framework uses [`SecondaryToPrimaryMapper`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/SecondaryToPrimaryMapper.java)
that tells (usually) based on the resource which primary resource reconciliation to trigger.
The mapping is usually done based on the owner reference or annotation on the secondary resource. 
(But not always, as we will see)

It is important to realize that if a resource triggers the reconciliation of a primary resource, that
resource naturally will be used during reconciliation. So the reconciler will need to access them. 
Therefore, InformerEventSource maintains a revers index [PrimaryToSecondaryIndex](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/DefaultPrimaryToSecondaryIndex.java), 
based on the result of the `SecondaryToPrimaryMapper`result. 

## Unified API for Related Resources

To access all related resources for a primary resource, the framework provides an API to access the related 
secondary resources using:

```java
Context.getSecondaryResources(Class<R> expectedType);
```

That will list all the related resources of a certain type, based on the `InformerEventSource`'s `PrimaryToSecondaryIndex`.
Based on that index, it reads the resources from the Informers cache. Note that since all those steps work
on top of indexes, those operations are very fast, usually O(1).

We mostly talk about InformerEventSource, but this works in similar ways for generalized EventSources concept, since
the [`EventSource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java#L93)
actually implements the `Set<R> getSecondaryResources(P primary);` method. That is just called from the context.

It is a bit more complex than that, since there can be multiple event sources for the same type, in that case
the union of the results is returned.

## Getting Resources Directly from Event Sources

Note that nothing stops you to directly access the resources in the cache (so not just through `getSecondaryResources(...)`):

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

TL;DR: `PrimaryToSecondaryMapper` is used to access secondary resources in `InformerEventSource` instead 
of the PrimaryToSecondaryIndex, thus `InfomerEventSource.getSecondaryResources(..)` will call this mapper
to get the target secondary resources. This is usually required in cases when the `SecondaryToPrimaryMapper`
is using the informer caches to list the target resources.

As we discussed, we provide a unified API to access related resources using `Context.getSecondaryResources(...)`.
The name `Secondary` refers to resources that a reconciler needs to take into account to properly reconcile a primary
resource. These resources cover more than only `child` resources as resources created by a reconciler are sometimes
called and which usually have an owner reference pointing to the primary (and, typically, custom) resource. These also
cover `related` resources (which might or might not be managed by Kubernetes) that serve as input for reconciliations.

There are cases where the SDK needs more information than what is readily available, in particular when some of these
secondary resources do not have owner references or anything direct link to the primary resource they are associated
with.

As an example we provide, consider a `Job` primary resource which can be assigned to run on a cluster, represented by a
`Cluster` resource.
Multiple jobs can run on a given cluster so multiple `Job` resources can reference the same `Cluster` resource. However,
a `Cluster` resource should not know about `Job` resources as this information is not part of what a cluster *is*.
However, when a cluster changes, we might want to redirect the associated jobs to other clusters. Our reconciler
therefore needs to figure out which `Job` (primary) resources are associated with the changed `Cluster` (secondary)
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

This will trigger all the related `Jobs` if the related cluster changes. Also, the maintaining the `PrimaryToSecondaryIndex`.
So we can use the `getSecondaryResources` in the `Job` reconciler to access the cluster.
However, there is an issue, what if now there is a new `Job` created? The new job does not propagate
automatically to `PrimaryToSecondaryIndex` in the `InformerEventSource` of the `Cluster`. That re-indexing
happens where there is an event received for the `Cluster` and triggers all the `Jobs` again.
Until that would happen again you could not use `getSecondaryResources` for the new `Job`, since the new
job won't bre present in the reverse index.

You could access the Cluster directly from cache though in the reconciler:

```java 

@Override
public UpdateControl<Job> reconcile(Job resource, Context<Job> context) {

    clusterInformer.get(new ResourceID(job.getSpec().getClusterName(), job.getMetadata().getNamespace()));

    // omitted details
}
```

But if you still want to use the unified API (thus `context.getSecondaryResources()`), we have to add 
`PrimaryToSecondaryMapper`:

```java
clusterInformer.withPrimaryToSecondaryMapper( job -> 
        Set.of(new ResourceID(job.getSpec().getClusterName(), job.getMetadata().getNamespace())));
```

Using `PrimaryToSecondaryMapper` the InformerEventSource won't use the `PrimaryToSecondaryIndex`
to get the resources, instead will call this mapper and will get the resources based on its result.
In fact if this mapper is set the `PrimaryToSecondaryIndex` is not even initialized.

### Using Informer Indexes to Improve Performance

In the `SecondaryToPrimaryMapper` above we are looping through all the resources in the cache:

```java
context.getPrimaryCache()
              .list().filter(job -> job.getSpec().getClusterName().equals(cluster.getMetadata().getName()))
```

This can be inefficient in case there is a large number of primary (Job) resources. To make it more efficient, we can
 create an index in the underlying Informer, that indexed the target jobs for a cluster: 

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

From this point, we can use the index to get the target resources very efficiently:

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