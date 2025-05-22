---
title: Custom resource change guarantees for next reconciliation 
date: 2025-05-21
author: >-
 [Attila Mészáros](https://github.com/csviri)
---

We recently released v5.1 of Java Operator SDK. One of the highlights of this release is related to a topic of so-called
[allocated values](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#representing-allocated-values
) in Kubernetes.

To sum up the problem, for example, if we create a resource in our controller that has a generated identifier - 
in other words, the new resource cannot be addressed only by using the values from the `.spec` of the resource -
we have to store it, commonly in the `.status` of the custom resource. However, operator frameworks cache resources
using informers, so the update that you made to the status of the custom resource will just eventually get into 
the cache of the informer. If meanwhile some other event triggers the reconciliation, it can happen that you will 
see the stale custom resource in the cache (in another word, the cache is eventually consistent). This is a problem 
since you might not know at that point that the desired resources were already created, so it might happen that you try to 
create them again. 

Java Operator SDK now out of the box provides a utility class [`PrimaryUpdateAndCacheUtils`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/PrimaryUpdateAndCacheUtils.java)
if you use it, the framework guarantees that the next reconciliation will always receive the updated resource:

```java
 @Override
  public UpdateControl<StatusPatchCacheCustomResource> reconcile(
      StatusPatchCacheCustomResource resource,
      Context<StatusPatchCacheCustomResource> context) {
    
    // omitted code
    
    var freshCopy = createFreshCopy(resource); // need fresh copy just because we use the SSA version of update
    freshCopy
        .getStatus()
        .setValue(statusWithAllocatedValue());

    // using the utility instead of update control
    var updated =
        PrimaryUpdateAndCacheUtils.ssaPatchStatusAndCacheResource(resource, freshCopy, context);
    return UpdateControl.noUpdate();
  }
```

This utility class will do the magic, but how does it work? There are multiple ways to solve this problem, 
but ultimately, we only provided the mentioned approach. (If you want to dig deep, see this [PR](https://github.com/operator-framework/java-operator-sdk/pull/2800/files)).

The trick is to cache the resource of our update in an additional cache on top of the informer's cache.
If we read the resource, we first check if it is in the overlay cache and only read it from the Informers cache
if not present there. If the informer receives an event with that resource, we always remove the resource from the overlay 
cache. But this **works only** if the update is done **with optimistic locking**.
So if the update fails on conflict, we simply wait and poll the informer cache until there is a new resource version, apply your changes,
and try to update again with optimistic locking.

So why optimistic locking? (A bit simplified explanation) Note that if we do not update the resource with optimistic locking, it can happen that
another party does an update on the resource just before we do. The informer receives the event from another party's update,
if we would compare resource versions with this resource and the previously cached resource (response from our update), 
that would be different, and in general there is no elegant way to determine if this new version that 
informer receives an event from an update that happened before or after our update. 
(Note that informers watch can lose connection and other edge cases)

If we do an update with optimistic locking, it simplifies the situation, we can easily have strong guarantees.
Since we know if the update with optimistic locking is successful, we have the fresh resource in our cache. 
Thus, the next event we receive will be the one that is the result of our update or a newer one. 
So if we cache the resource in the overlay cache from the response, we know that with the next event, we can remove it from there.

