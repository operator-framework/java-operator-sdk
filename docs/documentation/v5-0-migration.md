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
3. Patching status through `UpdateControl` like the `patchStatus` method now by default
   uses Server Side Apply instead of simple patch. To use the former approach, use the feature flag 
   in [`ConfigurationService`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L400-L400)
   !!! IMPORTANT !!!
   Migration from a non-SSA based controller to SSA based controller can cause problems, due to known issues. 
   See the following [integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L71-L82) where it is demonstrated.
   Also, the related part of a [workaround](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L110-L116).
4. `ManagedDependentResourceContext` has been renamed to `ManagedWorkflowAndDependentResourceContext` and is accessed
   via the accordingly renamed `managedWorkflowAndDependentResourceContext` method.
5. Reconcile pre-condition is renamed simply to `Condition` in ( @Dependent annotation, and related builders)
   just rename related attributes 
