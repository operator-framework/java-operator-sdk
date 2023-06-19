---
title: Migrating from v4.3 to v4.4
description: Migrating from v4.3 to v4.4
layout: docs
permalink: /docs/v4-4-migration
---

# Migrating from v4.4 to v4.4

## Using SSA in Dependent Resources

From this version by default [Dependent Resources](https://javaoperatorsdk.io/docs/dependent-resources) uses
[Server Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/) to create and update
Kubernetes resources. A
new [default matching](https://github.com/java-operator-sdk/java-operator-sdk/blob/e95f9c8a8b8a8561c9a735e60fc5d82b7758df8e/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/KubernetesDependentResource.java#L163-L163)
algorithm is provided for `KubernetesDependentResource` that is based on `managedFields` of SSA. For details
see [SSABasedGenericKubernetesResourceMatcher](https://github.com/java-operator-sdk/java-operator-sdk/blob/e95f9c8a8b8a8561c9a735e60fc5d82b7758df8e/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/SSABasedGenericKubernetesResourceMatcher.java)

Since those features are hard to completely test, we there are feature flags provided to use the legacy behavior,
see those
in [ConfigurationService](https://github.com/java-operator-sdk/java-operator-sdk/blob/e95f9c8a8b8a8561c9a735e60fc5d82b7758df8e/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L268-L289)

Note that it is possible to override the related methods/behavior on class level when extending
the `KubernetesDependentResource`.
