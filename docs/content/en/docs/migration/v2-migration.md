---
title: Migrating from v1 to v2
description: Migrating from v1 to v2
layout: docs
permalink: /docs/v2-migration
---

# Migrating from v1 to v2

Version 2 of the framework introduces improvements, features and breaking changes for the APIs both
internal and user facing ones. The migration should be however trivial in most of the cases. For
detailed overview of all major issues until the release of
v`2.0.0` [see milestone on GitHub](https://github.com/java-operator-sdk/java-operator-sdk/milestone/1)
. For a summary and reasoning behind some naming changes
see [this issue](https://github.com/java-operator-sdk/java-operator-sdk/issues/655)

## User Facing API Changes

The following items are renamed and slightly changed:

- [`ResourceController`](https://github.com/java-operator-sdk/java-operator-sdk/blob/v1/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/ResourceController.java)
  interface is renamed
  to [`Reconciler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java)
  . In addition, methods:
    - `createOrUpdateResource` renamed to `reconcile`
    - `deleteResource` renamed to `cleanup`
- Events are removed from
  the [`Context`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Context.java)
  of `Reconciler` methods . The rationale behind this, is that there is a consensus now on the
  pattern that the events should not be used to implement a reconciliation logic.
- The `init` method is extracted from `ResourceController` / `Reconciler` to a separate interface
  called [EventSourceInitializer](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/EventSourceInitializer.java)
  that `Reconciler` should implement in order to register event sources. The method has been renamed
  to `prepareEventSources` and should now return a list of `EventSource` implementations that
  the `Controller` will automatically register. See
  also [sample](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/tomcat-operator/src/main/java/io/javaoperatorsdk/operator/sample/WebappReconciler.java)
  for usage.
- [`EventSourceManager`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/EventSourceManager.java)
  is now an internal class that users shouldn't need to interact with.
- [`@Controller`](https://github.com/java-operator-sdk/java-operator-sdk/blob/v1/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Controller.java)
  annotation renamed
  to [`@ControllerConfiguration`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java)
- The metrics use `reconcile`, `cleanup` and `resource` labels instead of `createOrUpdate`, `delete`
  and `cr`, respectively to match the new logic.

### Event Sources

- Addressing resources within event sources (and in the framework internally) is now changed
  from `.metadata.uid` to a pair of `.metadata.name` and optional `.metadata.namespace` of resource.
  Represented
  by [`ResourceID.`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/ResourceID.java)
-

The [`Event`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/Event.java)
API is simplified. Now if an event source produces an event it needs to just produce an instance of
this class.

- [`EventSource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java)
  is refactored, but the changes are trivial. 
  
