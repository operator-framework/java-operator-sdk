---
title: Version 5 Released! 
date: 2024-09-21
---

We are excited to announce that Java Operator SDK v5 has been released. This significant effort contains
various features and enhancements accumulated since the last major releases and required changes in our APIs.
Within this post, we will go through all the main changes and help you upgrade to this new version, and provide
a rationale behind the changes if necessary.

We will omit descriptions of changes that are trivial to update from the source code; feel free to contact
us if you have some trouble with updates.

## Various Changes 

- From this release, the minimal Java version is 17.
- Various deprecated APIs are removed. The migration should be trivial.

## Changes in low-level APIs

### Patch status with Server Side Apply

[Server Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/) is now a first-class citizen in the framework and
the default approach for patching the status resource.

Note that, especially migration from a non-SSA based pathing to an SSA based one can be problematic.
To continue to use a non-SSA based on, set [ConfigurationService.useSSAToPatchPrimaryResource](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L462) to `false`.

See problematic cases, and how to handle them in [StatusPatchSSAMigrationIT](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuspatchnonlocking/StatusPatchSSAMigrationIT.java).

### Adding finalizer with Server Side Apply

### Event Sources

#### Naming event sources

#### Multi-cluster support in InformerEventSource

#### All EventSource is now a ResourceEventSource

#### SecondaryToPrimaryMapper now checks resource types

#### InformerEventSource-related refactors

- Context independent
- Informer Configuration classes

### @ControllerConfiguration is now optional

You no longer have to annotate the reconciler with `@ControllerConfiuraion` annotation. 
This annotation is (one) way to override the default properties of a controller.
If the annotation is not present, the default values from the annotation are used.

PR: https://github.com/operator-framework/java-operator-sdk/pull/2203

### EventSourceInitializer and ErrorStatusHandler are removed

### Cloning accessing secondary resources

### Remove automated observed generation handling

## Dependent Resource related changes

### ResourceDescriminator is removed

### Read-only bulk dependent resources

### Multiple Dependents with Activation Condition

## Workflow related Changes

### @Workflow annotation

### Explicit workflow invocation

### Silent exception handling

### CRDPresentActivationCondition 

## Additional minor changes

## Deprecation removals
