---
title: How to guarantee allocated values for next reconciliation
date: 2025-05-22
author: >-
  [Attila Mészáros](https://github.com/csviri)
---

We recently released v5.1 of Java Operator SDK (JOSDK). One of the highlights of this release is related to a topic of
so-called
[allocated values](https://github.com/kubernetes/community/blob/master/contributors/devel/sig-architecture/api-conventions.md#representing-allocated-values
).

To describe the problem, let's say that our controller needs to create a resource that has a generated identifier, i.e.
a resource which identifier cannot be directely derived from the custom resource's desired state as specified in its
`spec` field. In order to record the fact that the resource was successfully created, and to avoid attempting to
recreate the resource again in subsequent reconciliations, it is typical for this type of controller to store the
generated identifier in the custom resource's `status` field.

The Java Operator SDK relies on the informers' cache to retrieve resources. These caches, however, are only guaranteed
to be eventually consistent. It could happen, then, that, if some other event occurs, that would result in a new
reconciliation, **before** the update that's been made to our resource status has the chance to be propagated first to
the cluster and then back to the informer cache, that the resource in the informer cache does **not** contain the latest
version as modified by the reconciler. This would result in a new reconciliation where the generated identifier would be
missing from the resource status and, therefore, another attempt to create the resource by the reconciler, which is not
what we'd like.

Java Operator SDK now provides a utility class [
`PrimaryUpdateAndCacheUtils`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/PrimaryUpdateAndCacheUtils.java)
to handle this particular use case. Using that overlay cache, your reconciler is guaranteed to see the most up-to-date
version of the resource on the next reconciliation:

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

    // using the utility instead of update control to patch the resource status
    var updated =
            PrimaryUpdateAndCacheUtils.ssaPatchStatusAndCacheResource(resource, freshCopy, context);
    return UpdateControl.noUpdate();
}
```

How does `PrimaryUpdateAndCacheUtils` work?
There are multiple ways to solve this problem, but ultimately, we only provide the solution described below. (If you
want to dig deep in alternatives, see
this [PR](https://github.com/operator-framework/java-operator-sdk/pull/2800/files)).

The trick is to cache the resource from the response of our update in an additional cache on top of the informer's
cache. If we read the resource, we first check if it is in the overlay cache and read it from there if present,
otherwise read it from the cache of the informer. If the informer receives an event with a fresh resource, we always
remove the resource from the overlay
cache, since that is a more recent resource. But this **works only** if our request update is using **with optimistic locking**.
So if the update fails on conflict, we simply wait and poll the informer cache until there is a new resource version,
and try to update again using the new resource (applied our changes again) with optimistic locking.

So why optimistic locking? (A bit simplified explanation) Note that if we do not update the resource with optimistic
locking, it can happen that
another party does an update on the resource just before we do. The informer receives the event from another party's
update,
if we would compare resource versions with this resource and the previously cached resource (response from our update),
that would be different, and in general there is no elegant way to determine if this new version that
informer receives an event from an update that happened before or after our update.
(Note that informer's watch can lose connection and other edge cases)

```mermaid
flowchart TD
    A["Update Resource with Lock"] --> B{"Is Successful"}
    B -- Fails on conflict --> D["Poll the Informer cache until resource changes"]
    D --> n4["Apply desired changes on the resource again"]
    B -- Yes --> n2["Still the original resource in informer cache"]
    n2 -- Yes --> C["Cache the resource in overlay cache"]
    n2 -- NO --> n3["We know there is already a fesh resource in Informer"]
    n4 --> A

```

If we do our update with optimistic locking, it simplifies the situation, and we can have strong guarantees.
Since we know if the update with optimistic locking is successful, we have the up-to-date resource in our caches.
Thus, the next event we receive will be the one that is the result of our update
(or a newer one if somebody did an update after, but that is fine since it will contain our allocated values).
So if we cache the resource in the overlay cache from the response, we know that with the next event, we can remove it
from there.
Note that we store the result in overlay cache only if at that time we still have the original resource in cache.
If the cache already updated, that means that we already received a new event after our update,
so we have a fresh resource in the informer cache already.  
