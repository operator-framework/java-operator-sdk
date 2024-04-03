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
3. Updates through `UpdateControl` are by default now use SSA, this is true for adding finalizer and all
   the patch operations in `UpdateControl`. The update operations were removed. Migrating to SSA can be tricky
   in order to use non-SSA based patch you can use feature flag: 
   [`ConfigurationService.useSSAToPatchPrimaryResource()]`(https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L385-L385) to false.

   !!! IMPORTANT !!!

   See known issues with migration from non-SSA to SSA based status updates here:
   [integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L71-L82)
   where it is demonstrated. Also, the related part of
   a [workaround](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/StatusPatchSSAMigrationIT.java#L110-L116).
   
   SSA vs non-SSA resource handling required different approach for handling resource.    
   For SSA you send the "fully specified intent", what is a partial object that only includes the fields and values for which the user has an opinion.
   That mean that usually resource are created from scratch. 
   See SSA sample [here](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/sample/patchresourcewithssa/PatchResourceWithSSAReconciler.java#L7-L7).
   See non-SSA sample [here](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/sample/patchresourceandstatusnossa/PatchResourceAndStatusNoSSAReconciler.java#L16-L16).    

   Related automatic observed generation handling changes: 
   Automated Observed Generation (see features in docs), is automatically handled for non-SSA, even if
   the status sub-resource is not instructed to be updated. This is not true for SSA, observed generation is updated 
   only when patch status is instructed by `UpdateControl`.

4. `ManagedDependentResourceContext` has been renamed to `ManagedWorkflowAndDependentResourceContext` and is accessed
   via the accordingly renamed `managedWorkflowAndDependentResourceContext` method.
5. `ResourceDiscriminator` was removed. In most of the cases you can just delete the discriminator, everything should
    work without it by default. To optimize and handle special cases see the relevant section in [Dependent Resource documentation](/docs/dependent-resources#multiple-dependent-resources-of-same-type).
6. `ConfigurationService.getTerminationTimeoutSeconds` and associated overriding mechanism have been removed,
   use `Operator.stop(Duration)` instead.
7. `Operator.installShutdownHook()` has been removed, use `Operator.installShutdownHook(Duration)` instead
