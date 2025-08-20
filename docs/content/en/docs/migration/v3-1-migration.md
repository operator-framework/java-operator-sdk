---
title: Migrating from v3 to v3.1
layout: docs
permalink: /docs/v3-1-migration
---

## ReconciliationMaxInterval Annotation has been renamed to MaxReconciliationInterval

Associated methods on both the `ControllerConfiguration` class and annotation have also been
renamed accordingly.

## Workflows Impact on Managed Dependent Resources Behavior

Version 3.1 comes with a workflow engine that replaces the previous behavior of managed dependent
resources.
See [Workflows documentation](https://javaoperatorsdk.io/docs/documentation/dependent-resource-and-workflows/workflows/) for further details.
The primary impact after upgrade is a change of the order in which managed dependent resources
are reconciled. They are now reconciled in parallel with optional ordering defined using the
['depends_on'](https://github.com/java-operator-sdk/java-operator-sdk/blob/df44917ef81725c10bbcb772ab7b434d511b13b9/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/Dependent.java#L23-L23)
relation to define order between resources if needed. In v3, managed dependent resources were
implicitly reconciled in the order they were defined in.

## Garbage Collected Kubernetes Dependent Resources

In version 3 all Kubernetes Dependent Resource
implementing [`Deleter`](https://github.com/java-operator-sdk/java-operator-sdk/blob/bd063ccb7d55c110e96f24d2a10860d10aedfdb6/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/Deleter.java#L13-L13)
interface were meant to be also using owner references (thus garbage collected by Kubernetes).
In 3.1 there is a
dedicated [`GarbageCollected`](https://github.com/java-operator-sdk/java-operator-sdk/blob/bd063ccb7d55c110e96f24d2a10860d10aedfdb6/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/GarbageCollected.java#L28-L28)
interface to distinguish between Kubernetes resources meant to be garbage collected or explicitly
deleted. Please refer also to the `GarbageCollected` javadoc for more details on how this
impacts how owner references are managed.

The supporting classes were also updated. Instead
of [`CRUKubernetesDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/d99f65a736e9180e3f6de9a4239f80e47fc653fc/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/CRUKubernetesDependentResource.java)
there are two:

- [`CRUDKubernetesDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/bd063ccb7d55c110e96f24d2a10860d10aedfdb6/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/CRUDKubernetesDependentResource.java)
  that is `GarbageCollected`
- [`CRUDNoGCKubernetesDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/bd063ccb7d55c110e96f24d2a10860d10aedfdb6/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/CRUDNoGCKubernetesDependentResource.java)
  what is `Deleter` but not `GarbageCollected`

Use the one according to your use case. We anticipate that most people would want to use
`CRUDKubernetesDependentResource` whenever they have to work with Kubernetes dependent resources.