---
title: Migrating from v4.4 to v4.5
layout: docs
permalink: /docs/v4-5-migration
---

Version 4.5 introduces improvements related to event handling for Dependent Resources, more precisely the
[caching and event handling](https://javaoperatorsdk.io/docs/documentation/dependent-resource-and-workflows/dependent-resources/#caching-and-event-handling-in-kubernetesdependentresource)
features. As a result the Kubernetes resources managed using
[KubernetesDependentResource](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerEventSource.java#L72-L72)
or its subclasses, will add an annotation recording the resource's version whenever JOSDK updates or creates such
resources. This can be turned off using a
[feature flag](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L375-L375)
if causes some issues in your use case.

Using this feature, JOSDK now tracks versions of cached resources. It also uses, by default, that information to prevent
unneeded reconciliations that could occur when, depending on the timing of operations, an outdated resource would happen
to be in the cache. This relies on the fact that versions (as recorded by the `metadata.resourceVersion` field) are
currently implemented as monotonically increasing integers (though they should be considered as opaque and their
interpretation discouraged). Note that, while this helps preventing unneeded reconciliations, things would eventually
reach consistency even in the absence of this feature. Also, if this interpreting of the resource versions causes
issues, you can turn the feature off using the
[following feature flag](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L390-L390).
