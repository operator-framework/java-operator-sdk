---
title: Migrating from v2 to v3
description: Migrating from v2 to v3
layout: docs
permalink: /docs/v3-migration
---

# Migrating from v2 to v3

Version 3 introduces some breaking changes to APIs, however the migration to these changes should be trivial.

## Reconciler

- [`Reconciler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/67d8e25c26eb92392c6d2a9eb39ea6dddbbfafcc/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java#L16-L16)
  can throw checked exception (not just runtime exception), and that also can be handled by `ErrorStatusHandler`.
- `cleanup` method is extracted from the `Reconciler` interface to a
  separate [`Cleaner`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Cleaner.java)
  interface. Finalizers only makes sense that the `Cleanup` is implemented, from
  now finalizer is only added if the `Reconciler` implements this interface (or has managed dependent resources
  implementing `Deleter` interface, see dependent resource docs).
- [`Context`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Context.java#L9-L9)
  object of `Reconciler` now takes the Primary resource as parametrized type: `Context<MyCustomResource>`.
- [`ErrorStatusHandler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/67d8e25c26eb92392c6d2a9eb39ea6dddbbfafcc/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ErrorStatusHandler.java)
  result changed, it functionally has been extended to now prevent Exception to be retried and handles checked
  exceptions as mentioned above.  


## Event Sources

- Event Sources are now registered with a name. But [utility method](https://github.com/java-operator-sdk/java-operator-sdk/blob/92bfafd8831e5fb9928663133f037f1bf4783e3e/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/EventSourceInitializer.java#L33-L33) 
  is available to make it easy to [migrate](https://github.com/java-operator-sdk/java-operator-sdk/blob/92bfafd8831e5fb9928663133f037f1bf4783e3e/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageStandaloneDependentsReconciler.java#L51-L52)
  to a default name.  
- [InformerEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/92bfafd8831e5fb9928663133f037f1bf4783e3e/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerEventSource.java#L75-L75)
  constructor changed to reflect additional functionality in a non backwards compatible way. All the configuration
  options from the constructor where moved to [`InformerConfiguration`](https://github.com/java-operator-sdk/java-operator-sdk/blob/f6c6d568ea0a098e11beeeded20fe70f9c5bf692/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/InformerConfiguration.java)
  . See sample usage in [`WebPageReconciler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/f6c6d568ea0a098e11beeeded20fe70f9c5bf692/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageReconciler.java#L56-L59)
  .
- `PrimaryResourcesRetriever` was renamed to `SecondaryToPrimaryMapper`
- `AssociatedSecondaryResourceIdentifier` was renamed to `PrimaryToSecondaryMapper`
- `getAssociatedResource` is now renamed to get `getSecondaryResource` in multiple places 