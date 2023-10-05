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
if causes some issues in your use case.

This functionality by default will also parse field `metadata.resourceVersion` of
managed resources. Again by default just in case of Dependent Resource are used.

This helps to ensure that in the cache resources are with monotonically increasing resources version,
with all the additional consistency guarantees that mentioned features above provide.
In other words (and more precisely) if this is not done, it can happen in rare corner cases that for a
very short time cache will contain a resource with older version, however there was already a newer
version present before; so resource in the cache can jump to an older version (again for a short period of time).
Note that this will eventually resolve, so eventual consistency is guaranteed in all cases.

While Kubernetes resource versions should be considered as opaque and their interpretation discouraged, Kubernetes has,
at least so far, implemented them as a monotonically increasing integer. As a result, JOSDK will currently use
this information to support some corner cases. Of course, should this change, JOSDK will revise its implementation.
Also, if this interpreting of the resource versions causes issues, you can turn the feature off using the
[following feature flag](https://github.com/java-operator-sdk/java-operator-sdk/blob/73b1d8db926a24502c3a70da34f6bcac4f66b4eb/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L390-L390).
