---
title: Working with EventSource caches
weight: 48
---

As described in [Event sources and related topics](eventing.md) event sources are the backbone
for caching resources and triggering reconciliation of primary resources related
to these secondary resources.

In Kubernetes parlance, `Informers` handle that responsibility. Without going into
the details (there are plenty of good documents online regarding this topics), informers
watch resources, cache them, and emit an event whenever watched resources change.

`EventSource` generalizes this concept to also cover non-Kubernetes resources. Thus,
allowing caching of external resources, and triggering reconciliation when those change.

## The InformerEventSource

The underlying informer implementation comes from the Fabric8 client,
called [DefaultSharedIndexInformer](https://github.com/fabric8io/kubernetes-client/blob/main/kubernetes-client/src/main/java/io/fabric8/kubernetes/client/informers/impl/DefaultSharedIndexInformer.java).
[InformerEventSource](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerEventSource.java)
in Java Operator SDK wraps informers from Fabric8 client, thus presenting a unified front to deal with Kubernetes and
non-Kubernetes resources with the `EventSource` architecture.

However, `InformerEventSource` also provide additional capabilities such as:

- recording the relations between primary and secondary resources so that the event source knows which primary resource
  to trigger a reconciler with whenever one of the cached secondary resources cached by the informer changes,
- setting up multiple informers for the same type if needed, for example to transparently watch multiple namespaces,
  without you having to worry about it,
- dynamically adding/removing watched namespaces, if needed
- and more, outside of the scope of this document.

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
        var mySecondaryResource = configMapEventSource.get(new ResourceID("name", "namespace"));
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
The name `Secondary` refers to resources that a reconciler needs to take into account to properly reconcile a primary
resource. These resources cover more than only `child` resources as resources created by a reconciler are sometimes
called and which usually have a owner reference pointing to the primary (and, typically, custom) resource. These also
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

Even writing a `SecondaryToPrimaryMapper` is not trivial in this case, if the cluster is updated, we want to trigger
all `Job`s that are referencing it. So we have to efficiently get the list of jobs, and return their ResourceIDs in
the mapper. So we need an index that maps `Cluster` to `Job`s. Here we can use indexing capabilities of the Informers:

```java

@Override
public List<EventSource<?, Job>> prepareEventSources(EventSourceContext<Job> context) {

    context.getPrimaryCache()
            .addIndexer(JOB_CLUSTER_INDEX,
                    (job -> List.of(indexKey(job.getSpec().getClusterName(), job.getMetadata().getNamespace()))));

    // omitted details
}
```

where index key is a String that uniquely identifies a Cluster:

```java
private String indexKey(String clusterName, String namespace) {
    return clusterName + "#" + namespace;
}
```

In the InformerEventSource for the cluster now we can get all the `Jobs` for the `Cluster` using this index:

```java

InformerEventSource<Job, Cluster> clusterInformer =
        new InformerEventSource(
                InformerEventSourceConfiguration.from(Cluster.class, Job.class)
                        .withSecondaryToPrimaryMapper(
                                cluster ->
                                        context.getPrimaryCache()
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

But if you still want to use the unified API (thus `context.getSecondaryResources()`), we can add 
`PrimaryToSecondaryMapper`:

```java
clusterInformer.withPrimaryToSecondaryMapper( job -> 
        Set.of(new ResourceID(job.getSpec().getClusterName(), job.getMetadata().getNamespace())));
```

That will get the `Cluster` for the `Job` from the cache of `Cluster`'s `InformerEventSource`.
So it won't use the `PrimaryToSecondaryIndex`, that might be outdated, but instead will use the
`PrimaryToSecondaryMapper` to get
the target `Cluster` ids.
