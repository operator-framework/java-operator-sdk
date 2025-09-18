---
title: Accessing resources in caches
weight: 48
---

As described in [Event sources and related topics](eventing.md), event sources are the backbone
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

As we discussed, we provide a unified API to access related resources using `Context.getSecondaryResources(...)`.
This method was on purpose uses `Secondary` resource, since those are not only child resources - how
resources that are created by the reconciler are sometimes called in Kubernetes world - and usually have owner references for the custom resources;
neither related resources which are usually resources that serves as input for the primary (not managed). 
It is the union of both.

The issue is if we want to trigger reconciliation for a resource, that does not have an owner reference or other direct
association with the primary resource. 
Typically, if you have ConfigMap where you have input parameters for a set of primary resources, 
and the primary is actually referencing the secondary resource. 
In other words, having the name of the ConfigMap in the spec part of the primary resource.

As an example we provide, have a primary resource a `Job` that references a `Cluster` resource.
So multiple `Job` can reference the same `Cluster`, and we want to trigger `Job` reconciliation if cluster changes.
See full sample [here](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/primarytosecondary).
But the `Cluster` (the secondary resource) does not reference the `Jobs`.

Even writing a `SecondaryToPrimaryMapper` is not trivial in this case, if the cluster is updated, we want to trigger 
all `Jobs` that are referencing it. So we have to efficiently get the list of jobs, and return their ResourceIDs in
the mapper. So we need an index that maps `Cluster` to `Jobs`. Here we can use indexing capabilities of the Informers:

```java

@Override
public List<EventSource<?, Job>> prepareEventSources(EventSourceContext<Job> context) {

    context.getPrimaryCache()
            .addIndexer(JOB_CLUSTER_INDEX,
                    (job -> List.of(indexKey(job.getSpec().getClusterName(), job.getMetadata().getNamespace()))));
    
    // omitted details
}
```

where index key is a String that uniquely idetifies a Cluster:

```java
private String indexKey(String clusterName, String namespace) {
    return clusterName + "#" + namespace;
  }
```

In the InformerEventSource for the cluster now we can get all the `Jobs` for the `Cluster` using this index:

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

This will trigger all the related `Jobs` if a cluster changes. Also, the maintaining the `PrimaryToSecondaryIndex`.
So we can use the `getSecondaryResources` in the `Job` reconciler to access the cluster.
However, there is an issue, what if now there is a new `Job` created? The new job does not propagate
automatically to `PrimaryToSecondaryIndex` in the `InformerEventSource` of the `Cluster`. That re-indexing
happens where there is an event received for the `Cluster` and triggers all the `Jobs` again. 
Until that would happen again you could not use `getSecondaryResources` for the new `Job`.

You could access the Cluster directly from cache though in the reconciler:

```java 
@Override
public UpdateControl<Job> reconcile(Job resource, Context<Job> context) {

    clusterInformer.get(new ResourceID(job.getSpec().getClusterName(), job.getMetadata().getNamespace()));
    
    // omitted details
}
```

But if still want to use the unified API (thus `context.getSecondaryResources()`), we can add 
`PrimaryToSecondaryMapper`:

```java
clusterInformer.withPrimaryToSecondaryMapper( (PrimaryToSecondaryMapper<Job>)
  job -> Set.of(new ResourceID( job.getSpec().getClusterName(),job.getMetadata().getNamespace())));
```

That will get the `Cluster` for the `Job` from the cache of `Cluster`'s `InformerEventSource`.
So it won't use the `PrimaryToSecondaryIndex`, that might be outdated, but instead will use the `PrimaryToSecondaryMapper` to get
the target `Cluster` ids.
