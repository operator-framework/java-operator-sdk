---
title: Migrating from v5.1 to v5.2
description: Migrating from v5.1 to v5.2
---

Version 5.2 brings some breaking changes to certain components. This document provides
a migration guide for these changes. For all the new features, see the release notes.

## Custom ID types across multiple components using ResourceIDMapper and ResourceIDProvider 

Working with the id of a resource is needed across various components in the framework.
Until this version, the components provided by the framework assumed that you could easily
convert the id of a resource into a String representation. For example, 
[BulkDependentResources](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/BulkDependentResource.java#L46)
worked with a `Map<String,R>` of resources, where the id was always of type String. 

Mainly because of the need to manage external dependent resources more elegantly,
we introduced a cross-cutting concept: [`ResourceIDMapper`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/ResourceIDMapper.java),
which gets the ID of a resource. This is used across various components, see:

 - [`ExternalResourceCachingEventSource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/ExternalResourceCachingEventSource.java#L66)
 - [`ExternalBulkDependentResource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/ExternalBulkDependentResource.java)
 - [`AbstractExternalDependentResource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/AbstractExternalDependentResource.java#L39)
   and its subclasses.
   
We also added [`ResourceIDProvider`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/ResourceIDProvider.java),
which you can implement in your Pojo representing a resource. 

The easiest way to migrate to this new approach is to implement this interface for your (external) resource
and set the ID type generics for the components above. The default implementation of the `ResourceIDMapper`
works with `ResourceIDProvider` (see [related implementation](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/ResourceIDMapper.java#L52)).

If you cannot implement `ResourceIDProvider` because, for example, the class that represents the external resource is generated and final,
you can always set a custom `ResourceIDMapper` on the components above.

See also:
 - related issue: [link](https://github.com/operator-framework/java-operator-sdk/issues/2972)
 - related pull requests: 
   - [2970](https://github.com/operator-framework/java-operator-sdk/pull/2970)
   - [3020](https://github.com/operator-framework/java-operator-sdk/pull/3020)




