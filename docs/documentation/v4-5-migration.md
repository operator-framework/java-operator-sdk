---
title: Migrating from v4.4 to v4.5
description: Migrating from v4.4 to v4.5
layout: docs
permalink: /docs/v4-5-migration
---

# Migrating from v4.4 to v4.5

Version 4.5 introduces improvements related to event handling for Dependent Resources, more precisely the
[caching and event handling](https://javaoperatorsdk.io/docs/dependent-resources#caching-and-event-handling-in-kubernetesdependentresource)
features. As a result the Kubernetes resources managed using
[KubernetesDependentResource](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerEventSource.java#L72-L72)
or its subclasses, will add an annotation recording the resource's version whenever JOSDK updates or creates such
resources. This can be turned off using a
[feature flag](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L375-L375)
if this feature causes issues for your use case.

While Kubernetes resource versions should be considered as opaque and their interpretation discouraged, Kubernetes has,
at least so far, implemented them as a monotonically increasing integer. As a result, JOSDK will currently use
this information to support some corner cases. Of course, should this change, JOSDK will revise its implementation.
Also, if this interpreting of the resource versions causes issues, you can turn the feature off using the
[following feature flag](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L390-L390).

