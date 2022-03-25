---
title: Migrating from v2 to v3
description: Migrating from v2 to v3
layout: docs
permalink: /docs/v3-migration
---

# Migrating from v2 to v3

Version 3 introduces some breaking changes to the main and secondary APIs, however the migration to these changes are
trivial.

## Reconciler

- [`Reonciler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/67d8e25c26eb92392c6d2a9eb39ea6dddbbfafcc/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java#L16-L16)
  can throw checked Exception (not just runtime exception), and that also can be handled by `ErrorStatusHandler`.
- `cleanup` method is extracted from the `Reconcile` to a
  separate [`Cleaner`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Cleaner.java)
  interface. It was articulated also until now, that finalizers only makes sense that the `Cleanup` is implemented, from
  now finalizer is only added if the `Reconciler` implements this interface (or has managed dependent resources
  implementing `Deleter` interface, see dependent resource docs).
- [`Context`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Context.java#L9-L9)
  object of `Reconciler` now takes the Primary resource as parametrized type: `Context<MyCustomResource>`.
- [`ErrorStatusHandler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/67d8e25c26eb92392c6d2a9eb39ea6dddbbfafcc/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ErrorStatusHandler.java)
  result changed, it's functionally has been extended, now can prevent Exception to be retries and handle checked
  exceptions as mentioned above.  
