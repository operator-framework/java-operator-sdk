---
title: Migrating from v4.4 to v4.5
description: Migrating from v4.4 to v4.5
layout: docs
permalink: /docs/v4-5-migration
---

# Migrating from v4.4 to v4.5

This version some improvements how event are handled mainly in relation with Dependent Resources more precisely the
[caching and event handling](https://javaoperatorsdk.io/docs/dependent-resources#caching-and-event-handling-in-kubernetesdependentresource)
related functionality. As a result the kubernetes resources managed using
the [KubernetesDependentResource](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerEventSource.java#L72-L72)
will add an annotation to the resource containing the resource version for the update. This can be turned off by
feature
a [feature flag](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L375-L375)

This functionality by default - in order to cover some corner cases - will also parse resource version. While
this against of recommendations in Kubernetes, will work. If in some rare circumstances or some special usages
of Kubernetes this would cause a problem, it can be turned off by
the [following feature flag](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L390-L390).

