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

- explain also why creation of a new object
 
### Adding finalizer with Server Side Apply

### Event Sources

#### Naming event sources

#### Multi-cluster support in InformerEventSource

#### All EventSource is now a ResourceEventSource

#### SecondaryToPrimaryMapper now checks resource types

#### InformerEventSource-related refactors

- Context independent
- Informer Configuration classes

### EventSourceInitializer and ErrorStatusHandler are removed

### Cloning accessing secondary resources

### @ControllerConfiguration is now optional

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
