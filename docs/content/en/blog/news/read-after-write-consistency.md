---
title: Welcome read-cache-after-write consistency!!!
# todo issue with this?
#date: 2026-03-25
author: >-
  [Attila Mészáros](https://github.com/csviri)
---

**TL;DR:** 
In version 5.3.0 we introduced strong consistency guarantees for updates. 
You can now update resources (both your custom resoure and managed resource)
and the framwork will guaratee that these updates will be instantly visible, 
thus when accessing resources from caches; 
and naturally also for subsequent reconciliations.

```java 

public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {
    
    ConfigMap managedConfigMap = prepareConfigMap(webPage);
    // apply the resource with new API
    context.resourceOperations().serverSideApply(managedConfigMap);
    
    // fresh resource instantly available from our update in the caches
    var upToDateResource = context.getSecondaryResource(ConfigMap.class);
    
    // from now on built in update methods by default use this feature;
    // it is guaranteed that resource  changes will be visible for next reconciliation
    return UpdateControl.patchStatus(alterStatusObject(webPage));
}
```

In addition to that framework will automatically filter events for your updates, thus those
which are result of our own updates.

{{% alert color=success %}}
**These should significantly simplify controller development, and will make reconciliation
much simpler to reason about!**
{{% /alert %}}

This post will deep dive in this topic, explore the details and rationale behind it.

I briefly [talked about this](https://www.youtube.com/watch?v=HrwHh5Yh6AM&t=1387s) topic at KubeCon last year.

## Informers and eventual consistency

First we have to understand a fundamental building block of Kubernetes operators, called Informers.
Since there is plentiful accessible information about this topic, just in a nutshell, we have to know,
that these components:

1. Watches Kubernetes resources - K8S API sends events if a resource changes to the client 
   though a websocket. An usually contains the whole resource. (There are some exceptions, see Bookmarks).
   See details about watch as K8S API concept in the [official docs](https://kubernetes.io/docs/reference/using-api/api-concepts/#semantics-for-watch). 
2. Caches the actual latest state of the resource.
3. If an informer receives and event in which the `metadata.resourceVersion` is different from the version 
   in the cached resource it propagates and event further, in our case triggering the reconiliation.

A controller is usually composed of multiple informers, one is tracking the primary resources, and
there are also informers registered for each (secondary) resource we manage. 
Informers are great since we don't have to poll the Kubernetes API, it is push based; and they provide 
a cache, so reconciliations are very fast since they work on top of cached resources.

Now let's take a look on the flow when we do an update to a resources. Let's say we manage a Pod from our
controller:





TODO
- we do not cover deletes 
- thank to shaw

   



