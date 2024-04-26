---
title: Migrating from v4.7 to v5.0
description: Migrating from v4.7 to v5.0
layout: docs
permalink: /docs/v5-0-migration
---

# Migrating from v4.7 to v5.0

## API Tweaks

1. [Result of managed dependent resources](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/managed/ManagedDependentResourceContext.java#L55-L57)
   is not `Optional` anymore. In case you use this result, simply use the result
   objects directly.
2. `EventSourceInitializer` is not a separate interface anymore. It is part of the `Reconciler` interface with a
   default implementation. You can simply remove this interface from your reconciler. The
   [`EventSourceUtils`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/EventSourceUtils.java#L11-L11)
   now contains all the utility methods used for event sources naming that were previously defined in
   the `EventSourceInitializer` interface.
3. Event sources are now explicitly named (via the `name` method of the `EventSource` interface). Built-in event sources
   implementation have been updated to allow you to specify a name when instantiating them. If you don't provide a name
   for your `EventSource` implementation (for example, by using its default, no-arg constructor), one will be
   automatically generated. This simplifies the API to define event source to
   `List<EventSource> prepareEventSources(EventSourceContext<P> context)`.
   !!! IMPORTANT !!!
   If you use dynamic registration of event sources, be sure to name your event sources explicitly as letting JOSDK name
   them automatically might result in duplicated event sources being registered as JOSDK relies on the name to identify
   event sources and concurrent, dynamic registration might lead to identical event sources having different generated
   names, thus leading JOSDK to consider them as different and hence, register them multiple times.
4. Updates through `UpdateControl` now
   use [Server Side Apply (SSA)](https://kubernetes.io/docs/reference/using-api/server-side-apply/) by default to add
   the finalizer and for all
   the patch operations in `UpdateControl`. The update operations were removed. If you do not wish to use SSA, you can
   deactivate the feature using `ConfigurationService.useSSAToPatchPrimaryResource` and
   related `ConfigurationServiceOverrider.withUseSSAToPatchPrimaryResource`.

   !!! IMPORTANT !!!

   See known issues with migration from non-SSA to SSA based status updates here:
   [integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L71-L82)
   where it is demonstrated. Also, the related part of
   a [workaround](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L110-L116).

   Related automatic observed generation handling changes:
   Automated Observed Generation (see features in docs), is automatically handled for non-SSA, even if
   the status sub-resource is not instructed to be updated. This is not true for SSA, observed generation is updated
   only when patch status is instructed by `UpdateControl`.

5. `ManagedDependentResourceContext` has been renamed to `ManagedWorkflowAndDependentResourceContext` and is accessed
   via the accordingly renamed `managedWorkflowAndDependentResourceContext` method.
6. `ResourceDiscriminator` was removed. In most of the cases you can just delete the discriminator, everything should
   work without it by default. To optimize and handle special cases see the relevant section
   in [Dependent Resource documentation](/docs/dependent-resources#multiple-dependent-resources-of-same-type).
7. `ConfigurationService.getTerminationTimeoutSeconds` and associated overriding mechanism have been removed,
   use `Operator.stop(Duration)` instead.
8. `Operator.installShutdownHook()` has been removed, use `Operator.installShutdownHook(Duration)` instead
