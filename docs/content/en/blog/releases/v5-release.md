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

## Minimal Java version is 17

## Changes in low-level APIs

### Multi-cluster support in InformerEventSource

### Patch status with Server Side Apply

- explain also why creation of a new object
 
### Adding finalizer with Server Side Apply

### Naming event sources

### EventSourceInitializer and ErrorStatusHandler are removed

### Cloning accessing secondary resources

### All EventSource is now a ResourceEventSource

### SecondaryToPrimaryMapper now checks resource types

### InformerEventSource-related changes

- Context independent
- Informer Configuration classes
- Todo show diff

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
