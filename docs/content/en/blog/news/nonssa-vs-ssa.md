---
title: From client side to server-side apply
date: 2025-02-17
---

From version 5 of Java Operator SDK [server side apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/)
is a first-class feature, and used by default to update resources. Since, as we will see,
unfortunately (or fortunately) to use is it requires changes for your reconciler implementation.

For this reason, we prepared a feature flag, which you can flip if not prepared to migrate yet:
[`ConfigurationService.useSSAToPatchPrimaryResource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L493)

Setting this flag to false will make the operations done by `UpdateControl` using the former approach (not SSA).
As well as adding the finalizer with SSA if needed

For dependent resources a separate flag exists (this was true also before v5) to use SSA or not:


## Resource handling without and with SSA

Until version 5 changing primary resource through `UpdateControl` did not use server-side apply. 
So usually the implementation of reconciler looked something like this:

```java

 @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {
        
    reconcileLogicForManagedResources(webPage);
    webPage.setStatus(updatedStatusForWebPage(webPage));
    
    return UpdateControl.patchStatus(webPage);
  }

```

In other words, after reconciliation of managed resources the reconcile updated the status on the
primary resource passed as argument to the reconciler.
Such changes on primary are fine since we don't work directly with the cached object, the argument is
already cloned.

So how does this change with SSA?



