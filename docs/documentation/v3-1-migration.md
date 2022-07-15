---
title: Migrating from v3 to v3.1
description: Migrating from v3 to v3.1
layout: docs
permalink: /docs/v3-1-migration
---

# Migrating from v3 to v3.1

## Workflows Impact on Managed Dependent Resources Behavior

Version 3.1 comes with a workflow engine that replaces the previous behavior of managed dependent resources.
See Workflows documentation for further details.
The primary impact after upgrade, is that if there is a list of managed dependent resource, for now those
were reconciled in order, in the new version are reconciled in parallel. Use
['depends_on'](https://github.com/java-operator-sdk/java-operator-sdk/blob/df44917ef81725c10bbcb772ab7b434d511b13b9/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/Dependent.java#L23-L23)
relation to define order between resources if needed.

## Garbage Collected Kubernetes Dependent Resources

In version 3 all Kubernetes Dependent Resource
implementing [`Deleter`](https://github.com/java-operator-sdk/java-operator-sdk/blob/bd063ccb7d55c110e96f24d2a10860d10aedfdb6/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/Deleter.java#L13-L13)
interface were meant to be also using owner references (thus garbage collected by Kubernetes),
if not configured otherwise. In 3.1 there is a
dedicated [`GarbageCollected`](https://github.com/java-operator-sdk/java-operator-sdk/blob/bd063ccb7d55c110e96f24d2a10860d10aedfdb6/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/GarbageCollected.java#L28-L28)
interface to distinguish between a Kubernetes resource that mean to be garbage collected or explicitly deleted.
See also the javadoc how does this impacts the behavior adding owner references.
Note that `GarbageCollected` interface extends `Deleter`, so the dependent resource becomes the deleter automatically by
implementing this interface.

The supporting classes were also updated. Instead
of [`CRUKubernetesDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/d99f65a736e9180e3f6de9a4239f80e47fc653fc/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/CRUKubernetesDependentResource.java)
there are two:

- [`CRUDKubernetesDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/bd063ccb7d55c110e96f24d2a10860d10aedfdb6/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/CRUDKubernetesDependentResource.java)
  that is `GarbageCollected`
- [`CRUDNoGCKubernetesDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/bd063ccb7d55c110e96f24d2a10860d10aedfdb6/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/CRUDNoGCKubernetesDependentResource.java)
  what is `Deleter` but not `GarbageCollected`

Use the one according to your use case. 