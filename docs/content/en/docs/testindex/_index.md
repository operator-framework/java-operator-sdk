---
title: Integration Test Index
weight: 85
---

This document provides an index of all integration tests annotated with @Sample.

These serve also as samples for various use cases. You are encouraged to improve both the tests and/or descriptions.

## Contents

### Base API

- [Concurrent Reconciliation of Multiple Resources](#concurrencyit)
- [Operator Startup with Informer Errors](#informererrorhandlerstartit)
- [Leader Election with Insufficient Permissions](#leaderelectionpermissionit)
- [Cleanup handler for built-in Kubernetes resources](#builtinresourcecleanerit)
- [Dynamically Changing Watched Namespaces](#changenamespaceit)
- [Implementing Cleanup Logic with Cleaner Interface](#cleanerforreconcilerit)
- [Cleanup Finalizer Removal Without Conflicts](#cleanupconflictit)
- [Cluster-scoped resource reconciliation](#clusterscopedresourceit)
- [Concurrent Finalizer Removal by Multiple Reconcilers](#concurrentfinalizerremovalit)
- [Event filtering for create and update operations](#createupdateinformereventsourceeventfilterit)
- [Event Filtering with Previous Annotation Disabled](#previousannotationdisabledit)
- [Reconciling Non-Custom Kubernetes Resources with Status Updates](#kubernetesresourcestatusupdateit)
- [Dynamic Generic Event Source Registration](#dynamicgenericeventsourceregistrationit)
- [Error Status Handler for Failed Reconciliations](#errorstatushandlerit)
- [Custom Event Source for Periodic Reconciliation](#eventsourceit)
- [Filtering Events for Primary and Secondary Resources](#filterit)
- [Working with GenericKubernetesResource for Dynamic Resource Types](#generickubernetesresourcehandlingit)
- [Graceful Operator Shutdown with Reconciliation Timeout](#gracefulstopit)
- [Using Informer Event Source to Watch Secondary Resources](#informereventsourceit)
- [Watching resources in a remote Kubernetes cluster](#informerremoteclusterit)
- [Label Selector for Custom Resource Filtering](#labelselectorit)
- [Leader election with namespace change handling](#leaderelectionchangenamespaceit)
- [Manually managing observedGeneration in status](#manualobservedgenerationit)
- [Maximum Reconciliation Interval Configuration](#maxintervalit)
- [Maximum Reconciliation Interval After Retry](#maxintervalafterretryit)
- [Multiple reconcilers for the same resource type](#multiplereconcilersametypeit)
- [Managing Multiple Secondary Event Sources](#multiplesecondaryeventsourceit)
- [Handling Multiple CRD Versions](#multiversioncrdit)
- [Skipping status updates when next reconciliation is imminent](#nextreconciliationimminentit)
- [Patching resource and status without Server-Side Apply](#patchresourceandstatusnossait)
- [Patching resource and status with Server-Side Apply](#patchresourceandstatuswithssait)
- [Patching Resources with Server-Side Apply (SSA)](#patchresourcewithssait)
- [Per-resource polling event source implementation](#perresourcepollingeventsourceit)
- [Using Primary Indexer for Secondary Resource Mapping](#primaryindexerit)
- [Primary to Secondary Resource Mapping](#primarytosecondaryit)
- [Issues When Primary-to-Secondary Mapper Is Missing](#primarytosecondarymissingit)
- [Rate Limiting Reconciliation Executions](#ratelimitit)
- [Automatic Retry for Failed Reconciliations](#retryit)
- [Maximum Retry Attempts Configuration](#retrymaxattemptit)
- [Basic reconciler execution](#reconcilerexecutorit)
- [Server-Side Apply Finalizer Field Manager Issue](#ssafinalizerissueit)
- [Server-Side Apply Finalizer Removal on Spec Update](#ssaspecupdateit)
- [Accessing Secondary Resources During Operator Startup](#startupsecondaryaccessit)
- [Status patch caching for consistency](#statuspatchcacheit)
- [Status Patching Without Optimistic Locking for Non-SSA](#statuspatchnotlockingfornonssait)
- [Migrating Status Patching from Non-SSA to SSA](#statuspatchssamigrationit)
- [Status Update Locking and Concurrency Control](#statusupdatelockingit)
- [Status Subresource Updates](#subresourceupdateit)
- [Unmodifiable Parts in Dependent Resources](#unmodifiabledependentpartit)
- [Update Status in Cleanup and Reschedule](#updatestatusincleanupandrescheduleit)

### Dependent Resources

- [Bulk Dependent Resource Deleter Implementation](#bulkdependentdeleterit)
- [Bulk Dependent Resources with Ready Conditions](#bulkdependentwithconditionit)
- [Managing External Bulk Resources](#bulkexternaldependentit)
- [Bulk Dependent Resources with Managed Workflow](#managedbulkdependentit)
- [Read-Only Bulk Dependent Resources](#readonlybulkdependentit)
- [Standalone Bulk Dependent Resources](#standalonebulkdependentit)
- [Cleanup handlers for managed dependent resources](#cleanerformanageddependentresourcesonlyit)
- [Create-Only Dependent Resources with Server-Side Apply](#createonlyifnotexistingdependentwithssait)
- [Annotation-Based Secondary Resource Mapping for Dependents](#dependentannotationsecondarymapperit)
- [Custom Annotation Keys for Resource Mapping](#dependentcustommappingannotationit)
- [Dependent Resources in Different Namespaces](#dependentdifferentnamespaceit)
- [Filtering Reconciliation Triggers from Dependent Resources](#dependentfilterit)
- [Event filtering for dependent resource operations](#dependentoperationeventfilterit)
- [Reusing Dependent Resource Instances Across Tests](#dependentreinitializationit)
- [Dependent Resources with Cross-References](#dependentresourcecrossrefit)
- [Server-Side Apply (SSA) with Dependent Resources](#dependentssamatchingit)
- [Migrating Dependent Resources from Legacy to SSA](#dependentssamigrationit)
- [External State Tracking in Dependent Resources](#externalstatedependentit)
- [Managing External Resources with Persistent State](#externalstateit)
- [Bulk External State Management with Persistent State](#externalstatebulkit)
- [Generic Kubernetes Dependent Resource (Managed)](#generickubernetesdependentmanagedit)
- [Generic Kubernetes Resource as Standalone Dependent](#generickubernetesdependentstandaloneit)
- [Kubernetes Native Garbage Collection for Dependent Resources](#kubernetesdependentgarbagecollectionit)
- [Managing Multiple Dependent Resources](#multipledependentresourceit)
- [Multiple Dependents of Same Type Without Discriminator](#multipledependentresourcewithnodiscriminatorit)
- [Multiple Managed Dependents of Same Type with Multi-Informer](#multipledependentsametypemultiinformerit)
- [Multiple Managed Dependents of Same Type Without Discriminator](#multiplemanageddependentnodiscriminatorit)
- [Managing Multiple Dependent Resources of the Same Type](#multiplemanageddependentsametypeit)
- [Multiple Managed External Dependents of Same Type](#multiplemanagedexternaldependentsametypeit)
- [Dependent Resource Shared by Multiple Owners](#multiownerdependenttriggeringit)
- [Blocking Previous Annotation for Specific Resource Types](#prevannotationblockreconcilerit)
- [Primary Resource Indexer with Dependent Resources](#dependentprimaryindexerit)
- [Primary to Secondary Dependent Resource](#primarytosecondarydependentit)
- [Operator restart and state recovery](#operatorrestartit)
- [Strict matching for Service resources](#servicestrictmatcherit)
- [Handling special Kubernetes resources without spec](#specialresourcesdependentit)
- [Using Legacy Resource Matcher with SSA](#ssawithlegacymatcherit)
- [Standalone Dependent Resources](#standalonedependentresourceit)
- [Sanitizing StatefulSet desired state for SSA](#statefulsetdesiredsanitizerit)

### Workflows

- [Complex Workflow with Multiple Dependents](#complexworkflowit)
- [Workflow Activation Based on CRD Presence](#crdpresentactivationconditionit)
- [Workflow Functions on Vanilla Kubernetes Despite Inactive Resources](#workflowactivationconditionit)
- [Managed Dependent Delete Condition](#manageddependentdeleteconditionit)
- [Multiple Dependents with Activation Conditions](#multipledependentwithactivationit)
- [Ordered Managed Dependent Resources](#orderedmanageddependentit)
- [Workflow Activation Cleanup](#workflowactivationcleanupit)
- [Workflow Activation Condition](#workflowactivationconditionit)
- [Comprehensive workflow with reconcile and delete conditions](#workflowallfeatureit)
- [Explicit Workflow Cleanup Invocation](#workflowexplicitcleanupit)
- [Workflow Explicit Invocation](#workflowexplicitinvocationit)
- [Dynamic Workflow Activation and Deactivation](#workflowmultipleactivationit)
- [Silent Workflow Exception Handling in Reconciler](#workflowsilentexceptionhandlingit)

---

# Base API

## ConcurrencyIT

**Concurrent Reconciliation of Multiple Resources**

Demonstrates the operator's ability to handle concurrent reconciliation of multiple resources. The test creates, updates, and deletes many resources simultaneously to verify proper handling of concurrent operations, ensuring thread safety and correct resource state management under load.


**Package:** [io.javaoperatorsdk.operator.baseapi](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi)

---

## InformerErrorHandlerStartIT

**Operator Startup with Informer Errors**

Demonstrates that the operator can start successfully even when informers encounter errors during startup, such as insufficient access rights. By setting stopOnInformerErrorDuringStartup to false, the operator gracefully handles permission errors and continues initialization, allowing it to operate with partial access.


**Package:** [io.javaoperatorsdk.operator.baseapi](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi)

---

## LeaderElectionPermissionIT

**Leader Election with Insufficient Permissions**

Verifies that the operator fails gracefully when leader election is configured but the service account lacks permissions to access lease resources. This test ensures proper error handling and messaging when RBAC permissions are insufficient for leader election functionality.


**Package:** [io.javaoperatorsdk.operator.baseapi](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi)

---

## BuiltInResourceCleanerIT

**Cleanup handler for built-in Kubernetes resources**

Demonstrates how to implement cleanup handlers (finalizers) for built-in Kubernetes resources like Service and Pod. These resources don't use generation the same way as custom resources, so this sample shows the proper approach to handle their lifecycle and cleanup logic.


**Package:** [io.javaoperatorsdk.operator.baseapi.builtinresourcecleaner](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/builtinresourcecleaner)

---

## ChangeNamespaceIT

**Dynamically Changing Watched Namespaces**

Demonstrates how to dynamically change the set of namespaces that an operator watches at runtime. This feature allows operators to add or remove namespaces from their watch list, including switching between specific namespaces and watching all namespaces. The test verifies that resources in newly added namespaces are reconciled and resources in removed namespaces are no longer watched.


**Package:** [io.javaoperatorsdk.operator.baseapi.changenamespace](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/changenamespace)

---

## CleanerForReconcilerIT

**Implementing Cleanup Logic with Cleaner Interface**

Demonstrates how to implement cleanup logic for custom resources using the Cleaner interface. When a reconciler implements Cleaner, the framework automatically adds a finalizer to resources and calls the cleanup method when the resource is deleted. This pattern is useful for cleaning up external resources or performing custom deletion logic. The test verifies finalizer handling, cleanup execution, and the ability to reschedule cleanup operations.


**Package:** [io.javaoperatorsdk.operator.baseapi.cleanerforreconciler](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/cleanerforreconciler)

---

## CleanupConflictIT

**Cleanup Finalizer Removal Without Conflicts**

Tests that finalizers are removed correctly during cleanup without causing conflicts, even when multiple finalizers are present and removed concurrently. This verifies the operator's ability to handle finalizer updates safely during resource deletion.


**Package:** [io.javaoperatorsdk.operator.baseapi.cleanupconflict](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/cleanupconflict)

---

## ClusterScopedResourceIT

**Cluster-scoped resource reconciliation**

Demonstrates how to reconcile cluster-scoped custom resources (non-namespaced). This test shows CRUD operations on cluster-scoped resources and verifies that dependent resources are created, updated, and properly cleaned up when the primary resource is deleted.


**Package:** [io.javaoperatorsdk.operator.baseapi.clusterscopedresource](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/clusterscopedresource)

---

## ConcurrentFinalizerRemovalIT

**Concurrent Finalizer Removal by Multiple Reconcilers**

Demonstrates safe concurrent finalizer removal when multiple reconcilers manage the same resource with different finalizers. Tests that finalizers can be removed concurrently without conflicts or race conditions, ensuring proper cleanup even when multiple controllers are involved.


**Package:** [io.javaoperatorsdk.operator.baseapi.concurrentfinalizerremoval](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/concurrentfinalizerremoval)

---

## CreateUpdateInformerEventSourceEventFilterIT

**Event filtering for create and update operations**

Shows how to configure event filters on informer event sources to control which create and update events trigger reconciliation. This is useful for preventing unnecessary reconciliation loops when dependent resources are modified by the controller itself.


**Package:** [io.javaoperatorsdk.operator.baseapi.createupdateeventfilter](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/createupdateeventfilter)

---

## PreviousAnnotationDisabledIT

**Event Filtering with Previous Annotation Disabled**

Tests event filtering behavior when the previous annotation feature for dependent resources is disabled. Verifies that update events are properly received and handled even without the annotation tracking mechanism that compares previous resource states.


**Package:** [io.javaoperatorsdk.operator.baseapi.createupdateeventfilter](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/createupdateeventfilter)

---

## KubernetesResourceStatusUpdateIT

**Reconciling Non-Custom Kubernetes Resources with Status Updates**

Demonstrates how to reconcile standard Kubernetes resources (like Deployments) instead of custom resources, and how to update their status subresource. This pattern is useful when building operators that manage native Kubernetes resources rather than custom resource definitions. The test verifies that the operator can watch, reconcile, and update the status of a Deployment resource.


**Package:** [io.javaoperatorsdk.operator.baseapi.deployment](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/deployment)

---

## DynamicGenericEventSourceRegistrationIT

**Dynamic Generic Event Source Registration**

Demonstrates dynamic registration of generic event sources during runtime. The test verifies that event sources can be dynamically added to a reconciler and properly trigger reconciliation when the associated resources change, enabling flexible event source management.


**Package:** [io.javaoperatorsdk.operator.baseapi.dynamicgenericeventsourceregistration](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/dynamicgenericeventsourceregistration)

---

## ErrorStatusHandlerIT

**Error Status Handler for Failed Reconciliations**

Demonstrates how to implement error status handlers that update resource status when reconciliations fail. The test verifies that error messages are properly recorded in the resource status after each failed retry attempt. This provides visibility into reconciliation failures and helps with debugging operator issues.


**Package:** [io.javaoperatorsdk.operator.baseapi.errorstatushandler](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/errorstatushandler)

---

## EventSourceIT

**Custom Event Source for Periodic Reconciliation**

Demonstrates how to implement custom event sources that trigger reconciliation on a periodic basis. The test verifies that reconciliations are triggered at regular intervals by a timer-based event source. This enables operators to perform periodic checks or updates independent of resource changes.


**Package:** [io.javaoperatorsdk.operator.baseapi.event](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/event)

---

## FilterIT

**Filtering Events for Primary and Secondary Resources**

Demonstrates how to implement event filters for both primary custom resources and secondary dependent resources. The test verifies that resource updates matching specific filter criteria are ignored and don't trigger reconciliation. This helps reduce unnecessary reconciliation executions and improve operator efficiency.


**Package:** [io.javaoperatorsdk.operator.baseapi.filter](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/filter)

---

## GenericKubernetesResourceHandlingIT

**Working with GenericKubernetesResource for Dynamic Resource Types**

Demonstrates how to use GenericKubernetesResource to work with Kubernetes resources dynamically without requiring compile-time type definitions. This approach is useful when building operators that need to manage arbitrary Kubernetes resources or when the resource types are not known at compile time. The test shows how to handle generic resources as dependent resources in a reconciler.


**Package:** [io.javaoperatorsdk.operator.baseapi.generickubernetesresourcehandling](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/generickubernetesresourcehandling)

---

## GracefulStopIT

**Graceful Operator Shutdown with Reconciliation Timeout**

Demonstrates how to configure graceful shutdown behavior with reconciliation termination timeouts. The test verifies that in-progress reconciliations are allowed to complete when the operator stops. This ensures clean shutdown without interrupting ongoing reconciliation work.


**Package:** [io.javaoperatorsdk.operator.baseapi.gracefulstop](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/gracefulstop)

---

## InformerEventSourceIT

**Using Informer Event Source to Watch Secondary Resources**

Demonstrates how to use InformerEventSource to watch changes in secondary resources (ConfigMaps) and trigger reconciliation when those resources are created, updated, or deleted. The test verifies that the reconciler responds to ConfigMap changes and updates the primary resource status accordingly.


**Package:** [io.javaoperatorsdk.operator.baseapi.informereventsource](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/informereventsource)

---

## InformerRemoteClusterIT

**Watching resources in a remote Kubernetes cluster**

Demonstrates how to configure an informer event source to watch resources in a different Kubernetes cluster from where the operator is running. This enables multi-cluster scenarios where an operator in one cluster manages resources in another cluster.


**Package:** [io.javaoperatorsdk.operator.baseapi.informerremotecluster](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/informerremotecluster)

---

## LabelSelectorIT

**Label Selector for Custom Resource Filtering**

Demonstrates how to configure label selectors to filter which custom resources an operator watches. The test verifies that only resources with matching labels trigger reconciliation. This allows operators to selectively manage a subset of custom resources based on their labels.


**Package:** [io.javaoperatorsdk.operator.baseapi.labelselector](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/labelselector)

---

## LeaderElectionChangeNamespaceIT

**Leader election with namespace change handling**

Tests that when an operator is not elected as leader, changing the watched namespaces does not start processing. This ensures that only the leader operator actively reconciles resources, preventing conflicts in multi-instance deployments with leader election.


**Package:** [io.javaoperatorsdk.operator.baseapi.leaderelectionchangenamespace](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/leaderelectionchangenamespace)

---

## ManualObservedGenerationIT

**Manually managing observedGeneration in status**

Shows how to manually track and update the observedGeneration field in status to indicate which generation of the resource spec has been successfully processed. This is useful for providing clear feedback to users about reconciliation progress.


**Package:** [io.javaoperatorsdk.operator.baseapi.manualobservedgeneration](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/manualobservedgeneration)

---

## MaxIntervalIT

**Maximum Reconciliation Interval Configuration**

Demonstrates how to configure a maximum interval for periodic reconciliation triggers. The test verifies that reconciliation is automatically triggered at the configured interval even when there are no resource changes, enabling periodic validation and drift detection.


**Package:** [io.javaoperatorsdk.operator.baseapi.maxinterval](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/maxinterval)

---

## MaxIntervalAfterRetryIT

**Maximum Reconciliation Interval After Retry**

Tests that reconciliation is repeatedly triggered based on the maximum interval setting even after retries. This ensures periodic reconciliation continues at the configured maximum interval, maintaining eventual consistency regardless of retry attempts.


**Package:** [io.javaoperatorsdk.operator.baseapi.maxintervalafterretry](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/maxintervalafterretry)

---

## MultipleReconcilerSameTypeIT

**Multiple reconcilers for the same resource type**

Demonstrates how to register multiple reconcilers for the same custom resource type, with each reconciler handling different resources based on label selectors or other criteria. This enables different processing logic for different subsets of the same resource type.


**Package:** [io.javaoperatorsdk.operator.baseapi.multiplereconcilersametype](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/multiplereconcilersametype)

---

## MultipleSecondaryEventSourceIT

**Managing Multiple Secondary Event Sources**

Demonstrates how to configure and use multiple secondary event sources for a single reconciler. The test verifies that the reconciler is triggered by changes to different secondary resources and handles events from multiple sources correctly, including periodic event sources.


**Package:** [io.javaoperatorsdk.operator.baseapi.multiplesecondaryeventsource](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/multiplesecondaryeventsource)

---

## MultiVersionCRDIT

**Handling Multiple CRD Versions**

Demonstrates how to work with Custom Resource Definitions that have multiple API versions. The test shows how to configure multiple reconcilers for different versions of the same CRD, handle version-specific schemas, and deal with incompatible version conversions. It also demonstrates error handling through InformerStoppedHandler when deserialization fails due to schema incompatibilities between versions.


**Package:** [io.javaoperatorsdk.operator.baseapi.multiversioncrd](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/multiversioncrd)

---

## NextReconciliationImminentIT

**Skipping status updates when next reconciliation is imminent**

Shows how to use the nextReconciliationImminent flag to skip status updates when another reconciliation event is already pending. This optimization prevents unnecessary status patch operations when rapid consecutive reconciliations occur.


**Package:** [io.javaoperatorsdk.operator.baseapi.nextreconciliationimminent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/nextreconciliationimminent)

---

## PatchResourceAndStatusNoSSAIT

**Patching resource and status without Server-Side Apply**

Demonstrates how to patch both the primary resource metadata/spec and status subresource using traditional JSON merge patch instead of Server-Side Apply. This shows the legacy approach for updating resources when SSA is disabled.


**Package:** [io.javaoperatorsdk.operator.baseapi.patchresourceandstatusnossa](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/patchresourceandstatusnossa)

---

## PatchResourceAndStatusWithSSAIT

**Patching resource and status with Server-Side Apply**

Demonstrates how to use Server-Side Apply (SSA) to patch both the primary resource and its status subresource. SSA provides better conflict resolution and field management tracking compared to traditional merge patches, making it the recommended approach for resource updates.


**Package:** [io.javaoperatorsdk.operator.baseapi.patchresourcewithssa](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/patchresourcewithssa)

---

## PatchResourceWithSSAIT

**Patching Resources with Server-Side Apply (SSA)**

Demonstrates how to use Server-Side Apply (SSA) for patching primary resources in Kubernetes. The test verifies that the reconciler can patch resources using SSA, which provides better conflict resolution and field management compared to traditional update approaches, including proper handling of managed fields.


**Package:** [io.javaoperatorsdk.operator.baseapi.patchresourcewithssa](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/patchresourcewithssa)

---

## PerResourcePollingEventSourceIT

**Per-resource polling event source implementation**

Shows how to implement a per-resource polling event source where each primary resource has its own polling schedule to fetch external state. This is useful for integrating with external systems that don't support event-driven notifications.


**Package:** [io.javaoperatorsdk.operator.baseapi.perresourceeventsource](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/perresourceeventsource)

---

## PrimaryIndexerIT

**Using Primary Indexer for Secondary Resource Mapping**

Demonstrates how to use primary indexers to efficiently map secondary resources back to their primary resources. When a secondary resource (like a ConfigMap) changes, the primary indexer allows the framework to determine which primary resources should be reconciled. This pattern enables efficient one-to-many and many-to-many relationships between primary and secondary resources without polling or full scans.


**Package:** [io.javaoperatorsdk.operator.baseapi.primaryindexer](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/primaryindexer)

---

## PrimaryToSecondaryIT

**Primary to Secondary Resource Mapping**

Demonstrates many-to-one mapping between primary and secondary resources where multiple primary resources can reference the same secondary resource. The test verifies that changes in the secondary resource trigger reconciliation of all related primary resources, enabling shared resource patterns.


**Package:** [io.javaoperatorsdk.operator.baseapi.primarytosecondary](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/primarytosecondary)

---

## PrimaryToSecondaryMissingIT

**Issues When Primary-to-Secondary Mapper Is Missing**

Demonstrates the problems that occur when accessing secondary resources without a proper PrimaryToSecondaryMapper configured. The test shows that accessing secondary resources through the context fails without the mapper, while direct cache access works as a workaround, highlighting the importance of proper mapper configuration.


**Package:** [io.javaoperatorsdk.operator.baseapi.primarytosecondary](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/primarytosecondary)

---

## RateLimitIT

**Rate Limiting Reconciliation Executions**

Demonstrates how to implement rate limiting to control how frequently reconciliations execute. The test shows that multiple rapid resource updates are batched and executed at a controlled rate. This prevents overwhelming the system when resources change frequently.


**Package:** [io.javaoperatorsdk.operator.baseapi.ratelimit](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/ratelimit)

---

## RetryIT

**Automatic Retry for Failed Reconciliations**

Demonstrates how to configure automatic retry logic for reconciliations that fail temporarily. The test shows that failed executions are automatically retried with configurable intervals and max attempts. After a specified number of retries, the reconciliation succeeds and updates the resource status accordingly.


**Package:** [io.javaoperatorsdk.operator.baseapi.retry](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/retry)

---

## RetryMaxAttemptIT

**Maximum Retry Attempts Configuration**

Demonstrates how to configure a maximum number of retry attempts for failed reconciliations. The test verifies that the operator stops retrying after reaching the configured maximum attempts. This prevents infinite retry loops when reconciliations consistently fail.


**Package:** [io.javaoperatorsdk.operator.baseapi.retry](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/retry)

---

## ReconcilerExecutorIT

**Basic reconciler execution**

Demonstrates the basic reconciler execution flow including resource creation, status updates, and cleanup. This test verifies that a reconciler can create dependent resources (ConfigMap), update status, and properly handle cleanup when resources are deleted.


**Package:** [io.javaoperatorsdk.operator.baseapi.simple](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/simple)

---

## SSAFinalizerIssueIT

**Server-Side Apply Finalizer Field Manager Issue**

Demonstrates a potential issue with Server-Side Apply (SSA) when adding finalizers. When a resource is created with the same field manager used by the controller, adding a finalizer can unexpectedly remove other spec fields, showcasing field manager ownership conflicts in SSA.


**Package:** [io.javaoperatorsdk.operator.baseapi.ssaissue.finalizer](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/ssaissue/finalizer)

---

## SSASpecUpdateIT

**Server-Side Apply Finalizer Removal on Spec Update**

Demonstrates an issue with Server-Side Apply (SSA) where updating the resource spec without explicitly including the finalizer causes the finalizer to be removed. This highlights the importance of including all desired fields when using SSA to avoid unintended field removal.


**Package:** [io.javaoperatorsdk.operator.baseapi.ssaissue.specupdate](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/ssaissue/specupdate)

---

## StartupSecondaryAccessIT

**Accessing Secondary Resources During Operator Startup**

Verifies that reconcilers can properly access all secondary resources during operator startup, even when a large number of secondary resources exist. The test ensures that the informer cache is fully synchronized before reconciliation begins, allowing access to all related resources.


**Package:** [io.javaoperatorsdk.operator.baseapi.startsecondaryaccess](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/startsecondaryaccess)

---

## StatusPatchCacheIT

**Status patch caching for consistency**

Demonstrates how the framework caches status patches to ensure consistency when status is updated frequently. The cache guarantees that status values are monotonically increasing and always reflect the most recent state, even with rapid successive updates.


**Package:** [io.javaoperatorsdk.operator.baseapi.statuscache](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuscache)

---

## StatusPatchNotLockingForNonSSAIT

**Status Patching Without Optimistic Locking for Non-SSA**

Tests status update behavior when not using Server-Side Apply (SSA), verifying that optimistic locking is not enforced on status patches. The test also demonstrates proper field deletion when values are set to null, ensuring correct status management without SSA optimistic locking.


**Package:** [io.javaoperatorsdk.operator.baseapi.statuspatchnonlocking](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuspatchnonlocking)

---

## StatusPatchSSAMigrationIT

**Migrating Status Patching from Non-SSA to SSA**

Demonstrates the process and challenges of migrating status patching from traditional update methods to Server-Side Apply (SSA). Tests show a known Kubernetes issue where field deletion doesn't work correctly during migration, and provides a workaround by removing managed field entries from the previous update method.


**Package:** [io.javaoperatorsdk.operator.baseapi.statuspatchnonlocking](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuspatchnonlocking)

---

## StatusUpdateLockingIT

**Status Update Locking and Concurrency Control**

Demonstrates how the framework handles concurrent status updates and ensures no optimistic locking conflicts occur when updating status subresources. The test verifies that status updates can proceed independently of spec updates without causing version conflicts or requiring retries.


**Package:** [io.javaoperatorsdk.operator.baseapi.statusupdatelocking](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statusupdatelocking)

---

## SubResourceUpdateIT

**Status Subresource Updates**

Demonstrates how to properly update the status subresource of custom resources. The test verifies that status updates are handled correctly without triggering unnecessary reconciliations, and that concurrent spec and status updates are managed properly with optimistic locking and retry mechanisms.


**Package:** [io.javaoperatorsdk.operator.baseapi.subresource](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/subresource)

---

## UnmodifiableDependentPartIT

**Unmodifiable Parts in Dependent Resources**

Demonstrates how to preserve certain parts of a dependent resource from being modified during updates while allowing other parts to change. This test shows that initial data can be marked as unmodifiable and will remain unchanged even when the primary resource spec is updated, enabling partial update control.


**Package:** [io.javaoperatorsdk.operator.baseapi.unmodifiabledependentpart](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/unmodifiabledependentpart)

---

## UpdateStatusInCleanupAndRescheduleIT

**Update Status in Cleanup and Reschedule**

Tests the ability to update resource status during cleanup and reschedule the cleanup operation. This demonstrates that cleanup methods can perform status updates and request to be called again after a delay, enabling multi-step cleanup processes with status tracking.


**Package:** [io.javaoperatorsdk.operator.baseapi.updatestatusincleanupandreschedule](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/updatestatusincleanupandreschedule)

---

# Dependent Resources

## BulkDependentDeleterIT

**Bulk Dependent Resource Deleter Implementation**

Demonstrates implementation of a bulk dependent resource with custom deleter logic. This test extends BulkDependentTestBase to verify that bulk dependent resources can implement custom deletion strategies, managing multiple resources efficiently during cleanup operations.


**Package:** [io.javaoperatorsdk.operator.dependent.bulkdependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/bulkdependent)

---

## BulkDependentWithConditionIT

**Bulk Dependent Resources with Ready Conditions**

Tests bulk dependent resources with preconditions that control when reconciliation occurs. This demonstrates using ready conditions to ensure bulk operations only execute when the primary resource is in the appropriate state, coordinating complex multi-resource management.


**Package:** [io.javaoperatorsdk.operator.dependent.bulkdependent.condition](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/bulkdependent/condition)

---

## BulkExternalDependentIT

**Managing External Bulk Resources**

Demonstrates managing multiple external resources (non-Kubernetes) using bulk dependent resources. This pattern allows operators to manage a variable number of external resources based on primary resource specifications, handling creation, updates, and deletion of external resources at scale.


**Package:** [io.javaoperatorsdk.operator.dependent.bulkdependent.external](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/bulkdependent/external)

---

## ManagedBulkDependentIT

**Bulk Dependent Resources with Managed Workflow**

Demonstrates how to manage bulk dependent resources using the managed workflow approach. This test extends the base bulk dependent test to show how multiple instances of the same type of dependent resource can be created and managed together. The managed workflow handles the orchestration of creating, updating, and deleting multiple dependent resources based on the primary resource specification.


**Package:** [io.javaoperatorsdk.operator.dependent.bulkdependent.managed](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/bulkdependent/managed)

---

## ReadOnlyBulkDependentIT

**Read-Only Bulk Dependent Resources**

Demonstrates how to use read-only bulk dependent resources to observe and react to multiple existing resources without managing them. This test shows how an operator can monitor a collection of resources created externally and update the custom resource status based on their state, without creating or modifying them.


**Package:** [io.javaoperatorsdk.operator.dependent.bulkdependent.readonly](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/bulkdependent/readonly)

---

## StandaloneBulkDependentIT

**Standalone Bulk Dependent Resources**

Demonstrates how to use standalone bulk dependent resources to manage multiple resources of the same type efficiently. This test shows how bulk operations can be performed on a collection of resources without individual reconciliation cycles, improving performance when managing many similar resources.


**Package:** [io.javaoperatorsdk.operator.dependent.bulkdependent.standalone](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/bulkdependent/standalone)

---

## CleanerForManagedDependentResourcesOnlyIT

**Cleanup handlers for managed dependent resources**

Shows how to implement cleanup logic for managed dependent resources using the Cleaner interface. The framework automatically adds finalizers and invokes the cleanup method when the primary resource is deleted, ensuring proper cleanup of dependent resources.


**Package:** [io.javaoperatorsdk.operator.dependent.cleanermanageddependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/cleanermanageddependent)

---

## CreateOnlyIfNotExistingDependentWithSSAIT

**Create-Only Dependent Resources with Server-Side Apply**

Demonstrates how to configure a dependent resource that is only created if it doesn't exist, using Server-Side Apply (SSA). This test shows that when a resource already exists, the dependent resource implementation will not modify it, preserving any external changes.


**Package:** [io.javaoperatorsdk.operator.dependent.createonlyifnotexistsdependentwithssa](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/createonlyifnotexistsdependentwithssa)

---

## DependentAnnotationSecondaryMapperIT

**Annotation-Based Secondary Resource Mapping for Dependents**

Demonstrates using annotations instead of owner references to map secondary resources to primary resources in dependent resources. This approach is useful when owner references cannot be used (e.g., cross-namespace or cluster-scoped relationships), using special annotations to establish the relationship.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentannotationsecondarymapper](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentannotationsecondarymapper)

---

## DependentCustomMappingAnnotationIT

**Custom Annotation Keys for Resource Mapping**

Tests custom annotation-based mapping for dependent resources using configurable annotation keys instead of the default ones. This allows developers to customize which annotations are used to establish relationships between primary and secondary resources, providing flexibility for different naming conventions or avoiding conflicts.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentcustommappingannotation](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentcustommappingannotation)

---

## DependentDifferentNamespaceIT

**Dependent Resources in Different Namespaces**

Demonstrates how to manage dependent resources in a namespace different from the primary resource. This test shows how to configure dependent resources to be created in a specific namespace rather than inheriting the namespace from the primary resource. The test verifies full CRUD operations for a ConfigMap that lives in a different namespace than the custom resource that manages it.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentdifferentnamespace](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentdifferentnamespace)

---

## DependentFilterIT

**Filtering Reconciliation Triggers from Dependent Resources**

Demonstrates how to filter events from dependent resources to prevent unnecessary reconciliation triggers. This test shows how to configure filters on dependent resources so that only specific changes trigger a reconciliation of the primary resource. The test verifies that updates to filtered fields in the dependent resource do not cause the reconciler to execute, improving efficiency and avoiding reconciliation loops.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentfilter](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentfilter)

---

## DependentOperationEventFilterIT

**Event filtering for dependent resource operations**

Demonstrates how to configure event filters on dependent resources to prevent reconciliation loops. When a dependent resource is created or updated by the controller, the filter prevents those events from triggering unnecessary reconciliations.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentoperationeventfiltering](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentoperationeventfiltering)

---

## DependentReInitializationIT

**Reusing Dependent Resource Instances Across Tests**

Demonstrates that dependent resource instances can be safely reused across multiple operator start/stop cycles. This is particularly useful in CDI-managed environments like Quarkus, where dependent resources are managed as beans and should be reusable across test executions.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentreinitialization](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentreinitialization)

---

## DependentResourceCrossRefIT

**Dependent Resources with Cross-References**

Tests dependent resources that reference each other, creating interdependencies between multiple secondary resources. The test verifies that resources with circular or cross-references can be safely created, managed, and deleted without causing issues, even under concurrent operations with multiple primary resources.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentresourcecrossref](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentresourcecrossref)

---

## DependentSSAMatchingIT

**Server-Side Apply (SSA) with Dependent Resources**

Demonstrates how to use Server-Side Apply (SSA) with dependent resources and field manager matching. This test shows how SSA allows multiple controllers to manage different fields of the same resource without conflicts. The test verifies that changes made by different field managers are properly isolated, and that the operator only updates its own fields when changes occur, preserving fields managed by other controllers.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentssa](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentssa)

---

## DependentSSAMigrationIT

**Migrating Dependent Resources from Legacy to SSA**

Demonstrates migrating dependent resource management from legacy update methods to Server-Side Apply (SSA). Tests show bidirectional migration scenarios and field manager handling, including using the default fabric8 field manager to avoid creating duplicate managed field entries during migration.


**Package:** [io.javaoperatorsdk.operator.dependent.dependentssa](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/dependentssa)

---

## ExternalStateDependentIT

**External State Tracking in Dependent Resources**

Demonstrates managing dependent resources with external state that needs to be tracked independently of Kubernetes resources. This pattern allows operators to maintain state information for external systems or resources, ensuring proper reconciliation even when the external state differs from the desired Kubernetes resource state.


**Package:** [io.javaoperatorsdk.operator.dependent.externalstate](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/externalstate)

---

## ExternalStateIT

**Managing External Resources with Persistent State**

Demonstrates how to manage external resources (outside of Kubernetes) while maintaining their state in Kubernetes resources. This test shows a pattern for reconciling external systems by storing external resource identifiers in a ConfigMap. The test verifies that external resources can be created, updated, and deleted in coordination with Kubernetes resources, with the ConfigMap serving as a state store for external resource IDs.


**Package:** [io.javaoperatorsdk.operator.dependent.externalstate](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/externalstate)

---

## ExternalStateBulkIT

**Bulk External State Management with Persistent State**

Demonstrates managing multiple external resources with persistent state tracking using bulk dependent resources. This combines external state management with bulk operations, allowing operators to track and reconcile a variable number of external resources with persistent state that survives operator restarts.


**Package:** [io.javaoperatorsdk.operator.dependent.externalstate.externalstatebulkdependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/externalstate/externalstatebulkdependent)

---

## GenericKubernetesDependentManagedIT

**Generic Kubernetes Dependent Resource (Managed)**

Demonstrates how to use GenericKubernetesResource as a managed dependent resource. This test shows how to work with generic Kubernetes resources that don't have a specific Java model class, allowing the operator to manage any Kubernetes resource type dynamically.


**Package:** [io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentresourcemanaged](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/generickubernetesresource/generickubernetesdependentresourcemanaged)

---

## GenericKubernetesDependentStandaloneIT

**Generic Kubernetes Resource as Standalone Dependent**

Tests using GenericKubernetesResource as a standalone dependent resource. This approach allows operators to manage arbitrary Kubernetes resources without requiring specific Java classes for each resource type, providing flexibility for managing various resource types dynamically.


**Package:** [io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentstandalone](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/generickubernetesresource/generickubernetesdependentstandalone)

---

## KubernetesDependentGarbageCollectionIT

**Kubernetes Native Garbage Collection for Dependent Resources**

Demonstrates how to leverage Kubernetes native garbage collection for dependent resources using owner references. This test shows how dependent resources are automatically cleaned up by Kubernetes when the owner resource is deleted, and how to conditionally create or delete dependent resources based on the primary resource state. Owner references ensure that dependent resources don't outlive their owners.


**Package:** [io.javaoperatorsdk.operator.dependent.kubernetesdependentgarbagecollection](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/kubernetesdependentgarbagecollection)

---

## MultipleDependentResourceIT

**Managing Multiple Dependent Resources**

Demonstrates how to manage multiple dependent resources from a single reconciler. This test shows how a single custom resource can create, update, and delete multiple ConfigMaps (or other Kubernetes resources) as dependents. The test verifies that all dependent resources are created together, updated together when the primary resource changes, and properly cleaned up when the primary resource is deleted.


**Package:** [io.javaoperatorsdk.operator.dependent.multipledependentresource](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/multipledependentresource)

---

## MultipleDependentResourceWithNoDiscriminatorIT

**Multiple Dependents of Same Type Without Discriminator**

Demonstrates managing multiple dependent resources of the same type (ConfigMaps) without using discriminators. The framework uses resource names to differentiate between them, simplifying configuration when distinct names are sufficient for identification.


**Package:** [io.javaoperatorsdk.operator.dependent.multipledependentresourcewithsametype](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/multipledependentresourcewithsametype)

---

## MultipleDependentSameTypeMultiInformerIT

**Multiple Managed Dependents of Same Type with Multi-Informer**

Tests managing multiple dependent resources of the same type using separate informers for each. This approach allows for independent event handling and caching for resources of the same type, useful when different caching strategies or event filtering is needed for different instances.


**Package:** [io.javaoperatorsdk.operator.dependent.multipledependentsametypemultiinformer](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/multipledependentsametypemultiinformer)

---

## MultipleManagedDependentNoDiscriminatorIT

**Multiple Managed Dependents of Same Type Without Discriminator**

Demonstrates managing multiple managed dependent resources of the same type without explicit discriminators. The test verifies complete CRUD operations on multiple ConfigMaps, showing that resource names alone can differentiate between dependents when a discriminator is not needed.


**Package:** [io.javaoperatorsdk.operator.dependent.multipledrsametypenodiscriminator](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/multipledrsametypenodiscriminator)

---

## MultipleManagedDependentSameTypeIT

**Managing Multiple Dependent Resources of the Same Type**

Demonstrates how to manage multiple dependent resources of the same type from a single reconciler. This test shows how multiple ConfigMaps with the same type can be created, updated, and deleted as dependent resources of a custom resource, verifying proper CRUD operations and garbage collection.


**Package:** [io.javaoperatorsdk.operator.dependent.multiplemanageddependentsametype](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/multiplemanageddependentsametype)

---

## MultipleManagedExternalDependentSameTypeIT

**Multiple Managed External Dependents of Same Type**

Tests managing multiple external (non-Kubernetes) dependent resources of the same type. This demonstrates that operators can manage multiple instances of external resources simultaneously, handling their lifecycle including creation, updates, and deletion.


**Package:** [io.javaoperatorsdk.operator.dependent.multiplemanagedexternaldependenttype](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/multiplemanagedexternaldependenttype)

---

## MultiOwnerDependentTriggeringIT

**Dependent Resource Shared by Multiple Owners**

Demonstrates a dependent resource (ConfigMap) that is managed by multiple primary resources simultaneously. Tests verify that updates from any owner trigger proper reconciliation, owner references are correctly maintained, and the shared resource properly aggregates data from all owners.


**Package:** [io.javaoperatorsdk.operator.dependent.multipleupdateondependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/multipleupdateondependent)

---

## PrevAnnotationBlockReconcilerIT

**Blocking Previous Annotation for Specific Resource Types**

Tests the previous annotation blocklist feature, which prevents storing previous resource state annotations for specific resource types like Deployments. This optimization avoids unnecessary reconciliation loops for resources that have server-side mutations, improving performance and stability.


**Package:** [io.javaoperatorsdk.operator.dependent.prevblocklist](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/prevblocklist)

---

## DependentPrimaryIndexerIT

**Primary Resource Indexer with Dependent Resources**

Extends PrimaryIndexerIT to test primary resource indexing functionality with dependent resources. Demonstrates how custom indexes on primary resources can be used to efficiently query and access resources within dependent resource implementations, enabling performant lookups.


**Package:** [io.javaoperatorsdk.operator.dependent.primaryindexer](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/primaryindexer)

---

## PrimaryToSecondaryDependentIT

**Primary to Secondary Dependent Resource**

Demonstrates how to configure dependencies between dependent resources where one dependent resource (secondary) depends on another dependent resource (primary). This test shows how a Secret's creation can be conditioned on the state of a ConfigMap, illustrating the use of reconcile preconditions and dependent resource chaining.


**Package:** [io.javaoperatorsdk.operator.dependent.primarytosecondaydependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/primarytosecondaydependent)

---

## OperatorRestartIT

**Operator restart and state recovery**

Tests that an operator can be stopped and restarted while maintaining correct behavior. After restart, the operator should resume processing existing resources without losing track of their state, demonstrating proper state recovery and persistence.


**Package:** [io.javaoperatorsdk.operator.dependent.restart](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/restart)

---

## ServiceStrictMatcherIT

**Strict matching for Service resources**

Shows how to use a strict matcher for Service dependent resources that correctly handles Service-specific fields. This prevents unnecessary updates when Kubernetes adds default values or modifies certain fields, avoiding reconciliation loops.


**Package:** [io.javaoperatorsdk.operator.dependent.servicestrictmatcher](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/servicestrictmatcher)

---

## SpecialResourcesDependentIT

**Handling special Kubernetes resources without spec**

Demonstrates how to handle special built-in Kubernetes resources like ServiceAccount that don't have a spec field. These resources require different handling approaches since their configuration is stored directly in the resource body rather than in a spec section.


**Package:** [io.javaoperatorsdk.operator.dependent.specialresourcesdependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/specialresourcesdependent)

---

## SSAWithLegacyMatcherIT

**Using Legacy Resource Matcher with SSA**

Demonstrates using the legacy resource matcher with Server-Side Apply (SSA). The legacy matcher provides backward compatibility for matching logic while using SSA for updates, ensuring that resource comparisons work correctly even when migrating from traditional update methods to SSA.


**Package:** [io.javaoperatorsdk.operator.dependent.ssalegacymatcher](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/ssalegacymatcher)

---

## StandaloneDependentResourceIT

**Standalone Dependent Resources**

Demonstrates how to use standalone dependent resources that are managed independently without explicit workflow configuration. This test shows how dependent resources can be created and managed programmatically, with the dependent resource handling CRUD operations on a Kubernetes Deployment. The test verifies both creation and update scenarios, including cache updates when the dependent resource state changes.


**Package:** [io.javaoperatorsdk.operator.dependent.standalonedependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/standalonedependent)

---

## StatefulSetDesiredSanitizerIT

**Sanitizing StatefulSet desired state for SSA**

Shows how to properly sanitize StatefulSet resources before using Server-Side Apply. StatefulSets have immutable fields and server-managed fields that need to be removed from the desired state to prevent conflicts and unnecessary updates.


**Package:** [io.javaoperatorsdk.operator.dependent.statefulsetdesiredsanitizer](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/statefulsetdesiredsanitizer)

---

# Workflows

## ComplexWorkflowIT

**Complex Workflow with Multiple Dependents**

Demonstrates a complex workflow with multiple dependent resources (StatefulSets and Services) that have dependencies on each other. This test shows how to orchestrate the reconciliation of interconnected dependent resources in a specific order.


**Package:** [io.javaoperatorsdk.operator.workflow.complexdependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/complexdependent)

---

## CRDPresentActivationConditionIT

**Workflow Activation Based on CRD Presence**

Tests workflow activation conditions that depend on the presence of specific Custom Resource Definitions (CRDs). Dependent resources are only created when their corresponding CRDs exist in the cluster, allowing operators to gracefully handle optional dependencies and multi-cluster scenarios.


**Package:** [io.javaoperatorsdk.operator.workflow.crdpresentactivation](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/crdpresentactivation)

---

## WorkflowActivationConditionIT

**Workflow Functions on Vanilla Kubernetes Despite Inactive Resources**

Verifies that workflows function correctly on vanilla Kubernetes even when they include resources that are not available on the platform (like OpenShift Routes). The operator successfully reconciles by skipping inactive dependents based on activation conditions, demonstrating platform-agnostic operator design.


**Package:** [io.javaoperatorsdk.operator.workflow.getnonactivesecondary](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/getnonactivesecondary)

---

## ManagedDependentDeleteConditionIT

**Managed Dependent Delete Condition**

Demonstrates how to use delete conditions to control when dependent resources can be deleted. This test shows how the primary resource deletion can be blocked until dependent resources are properly cleaned up, ensuring graceful shutdown and preventing orphaned resources.


**Package:** [io.javaoperatorsdk.operator.workflow.manageddependentdeletecondition](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/manageddependentdeletecondition)

---

## MultipleDependentWithActivationIT

**Multiple Dependents with Activation Conditions**

Demonstrates how to use activation conditions with multiple dependent resources. This test shows how different dependent resources can be dynamically enabled or disabled based on runtime conditions, allowing flexible workflow behavior that adapts to changing requirements.


**Package:** [io.javaoperatorsdk.operator.workflow.multipledependentwithactivation](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/multipledependentwithactivation)

---

## OrderedManagedDependentIT

**Ordered Managed Dependent Resources**

Demonstrates how to control the order of reconciliation for managed dependent resources. This test verifies that dependent resources are reconciled in a specific sequence, ensuring proper orchestration when dependencies have ordering requirements.


**Package:** [io.javaoperatorsdk.operator.workflow.orderedmanageddependent](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/orderedmanageddependent)

---

## WorkflowActivationCleanupIT

**Workflow Activation Cleanup**

Demonstrates how workflow cleanup is handled when activation conditions are involved. This test verifies that resources are properly cleaned up on operator startup even when marked for deletion, ensuring no orphaned resources remain after restarts.


**Package:** [io.javaoperatorsdk.operator.workflow.workflowactivationcleanup](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowactivationcleanup)

---

## WorkflowActivationConditionIT

**Workflow Activation Condition**

Demonstrates how to use activation conditions to conditionally enable or disable parts of a workflow. This test shows how the workflow can adapt to different environments (e.g., vanilla Kubernetes vs. OpenShift) by activating only the relevant dependent resources based on runtime conditions.


**Package:** [io.javaoperatorsdk.operator.workflow.workflowactivationcondition](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowactivationcondition)

---

## WorkflowAllFeatureIT

**Comprehensive workflow with reconcile and delete conditions**

Demonstrates a complete workflow implementation including reconcile conditions, delete conditions, and ready conditions. Shows how to control when dependent resources are created or deleted based on conditions, and how to coordinate dependencies that must wait for others to be ready.


**Package:** [io.javaoperatorsdk.operator.workflow.workflowallfeature](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowallfeature)

---

## WorkflowExplicitCleanupIT

**Explicit Workflow Cleanup Invocation**

Tests explicit workflow cleanup invocation, demonstrating that workflow cleanup is called even when using explicit workflow invocation mode. This ensures that dependent resources are properly cleaned up during deletion regardless of how the workflow is invoked, maintaining consistent cleanup behavior.


**Package:** [io.javaoperatorsdk.operator.workflow.workflowexplicitcleanup](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowexplicitcleanup)

---

## WorkflowExplicitInvocationIT

**Workflow Explicit Invocation**

Demonstrates how to explicitly control when a workflow is invoked rather than having it run automatically on every reconciliation. This test shows how to programmatically trigger workflow execution and how cleanup is still performed even with explicit invocation.


**Package:** [io.javaoperatorsdk.operator.workflow.workflowexplicitinvocation](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowexplicitinvocation)

---

## WorkflowMultipleActivationIT

**Dynamic Workflow Activation and Deactivation**

Tests dynamic activation and deactivation of workflow dependents based on changing conditions. Demonstrates that dependents can be conditionally activated or deactivated during the resource lifecycle, with proper cleanup and recreation, and verifies that inactive dependents don't trigger reconciliation or maintain informers.


**Package:** [io.javaoperatorsdk.operator.workflow.workflowmultipleactivation](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowmultipleactivation)

---

## WorkflowSilentExceptionHandlingIT

**Silent Workflow Exception Handling in Reconciler**

Demonstrates handling workflow exceptions silently within the reconciler rather than propagating them. Tests verify that exceptions from dependent resources during both reconciliation and cleanup are captured in the result object, allowing custom error handling logic without failing the entire reconciliation.


**Package:** [io.javaoperatorsdk.operator.workflow.workflowsilentexceptionhandling](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowsilentexceptionhandling)

---

