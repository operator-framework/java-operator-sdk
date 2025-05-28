---
title: From legacy approach to server-side apply
date: 2025-02-25
author: >-
 [Attila Mészáros](https://github.com/csviri)
---

From version 5 of Java Operator SDK [server side apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/)
is a first-class feature and is used by default to update resources.
As we will see, unfortunately (or fortunately), using it requires changes for your reconciler implementation.

For this reason, we prepared a feature flag, which you can flip if you are not prepared to migrate yet:
[`ConfigurationService.useSSAToPatchPrimaryResource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L493)

Setting this flag to false will make the operations done by `UpdateControl` using the former approach (not SSA).
Similarly, the finalizer handling won't utilize SSA handling. 
The plan is to keep this flag and allow the use of the former approach (non-SSA) also in future releases. 

For dependent resources, a separate flag exists (this was true also before v5) to use SSA or not:
[`ConfigurationService.ssaBasedCreateUpdateMatchForDependentResources`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L373)


## Resource handling without and with SSA

Until version 5, changing primary resources through `UpdateControl` did not use server-side apply. 
So usually, the implementation of the reconciler looked something like this:

```java

 @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {
        
    reconcileLogicForManagedResources(webPage);
    webPage.setStatus(updatedStatusForWebPage(webPage));
    
    return UpdateControl.patchStatus(webPage);
  }

```

In other words, after the reconciliation of managed resources, the reconciler updates the status of the
primary resource passed as an argument to the reconciler.
Such changes on the primary are fine since we don't work directly with the cached object, the argument is
already cloned.

So, how does this change with SSA?
For SSA, the updates should contain (only) the "fully specified intent".
In other words, we should only fill in the values we care about.
In practice, it means creating a **fresh copy** of the resource and setting only what is necessary:

```java

@Override
public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {

    reconcileLogicForManagedResources(webPage);

    WebPage statusPatch = new WebPage();
    statusPatch.setMetadata(new ObjectMetaBuilder()
            .withName(webPage.getMetadata().getName())
            .withNamespace(webPage.getMetadata().getNamespace())
            .build());
    statusPatch.setStatus(updatedStatusForWebPage(webPage));

    return UpdateControl.patchStatus(statusPatch);
}
```

Note that we just filled out the status here since we patched the status (not the resource spec).
Since the status is a sub-resource in Kubernetes, it will only update the status part.

Every controller you register will have its default [field manager](https://kubernetes.io/docs/reference/using-api/server-side-apply/#managers).
You can override the field manager name using [`ControllerConfiguration.fieldManager`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ControllerConfiguration.java#L89).
That will set the field manager for the primary resource and dependent resources as well.

## Migrating to SSA

Using the legacy or the new SSA way of resource management works well.
However, migrating existing resources to SSA might be a challenge. 
We strongly recommend testing the migration, thus implementing an integration test where 
a custom resource is created using the legacy approach and is managed by the new approach.

We prepared an integration test to demonstrate how such migration, even in a simple case, can go wrong,
and how to fix it.

To fix some cases, you might need to [strip managed fields](https://kubernetes.io/docs/reference/using-api/server-side-apply/#clearing-managedfields)
from the custom resource.

See [`StatusPatchSSAMigrationIT`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuspatchnonlocking/StatusPatchSSAMigrationIT.java) for details.

Feel free to report common issues, so we can prepare some utilities to handle them.

## Optimistic concurrency control

When you create a resource for SSA as mentioned above, the framework will apply changes even if the underlying resource 
or status subresource is changed while the reconciliation was running.
First, it always forces the conflicts in the background as advised in [Kubernetes docs](https://kubernetes.io/docs/reference/using-api/server-side-apply/#using-server-side-apply-in-a-controller),
 in addition to that since the resource version is not set it won't do optimistic locking. If you still
want to have optimistic locking for the patch, use the resource version of the original resource:

```java
@Override
public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {

    reconcileLogicForManagedResources(webPage);

    WebPage statusPatch = new WebPage();
    statusPatch.setMetadata(new ObjectMetaBuilder()
            .withName(webPage.getMetadata().getName())
            .withNamespace(webPage.getMetadata().getNamespace())
            .withResourceVersion(webPage.getMetadata().getResourceVersion())
            .build());
    statusPatch.setStatus(updatedStatusForWebPage(webPage));

    return UpdateControl.patchStatus(statusPatch);
}
```
