---
title: Glossary
weight: 100
---

- **Primary Resource** - The resource representing the desired state that the controller works to achieve. While often a Custom Resource, it can also be a native Kubernetes resource (Deployment, ConfigMap, etc.).

- **Secondary Resource** - Any resource the controller needs to manage to reach the desired state represented by the primary resource. These can be created, updated, deleted, or simply read depending on the use case. For example, the `Deployment` controller manages `ReplicaSet` instances to realize the state represented by the `Deployment`. Here, `Deployment` is the primary resource while `ReplicaSet` is a secondary resource.

- **Dependent Resource** - A JOSDK feature that makes managing secondary resources easier. A dependent resource represents a secondary resource with associated reconciliation logic.

- **Low-level API** - SDK APIs that don't use features beyond the core [`Reconciler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java) interface (such as Dependent Resources or Workflows). See the [WebPage sample](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageReconciler.java). The same logic is also implemented using [Dependent Resource and Workflows](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageManagedDependentsReconciler.java).