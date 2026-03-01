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

This post will deep dive in this topic, and explain all the rational and background behind this topic.

## Informers and eventual consistency



