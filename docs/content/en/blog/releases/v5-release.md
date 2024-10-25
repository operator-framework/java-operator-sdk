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

## Naming changes

TODO add handy diff links here

## Changes in low-level APIs

### Server Side Apply 

[Server Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/) is now a first-class citizen in the framework and
the default approach for patching the status resource. That means patching the resource or it's status through `UpdateControl` and adding
the finalizer in the background.

Migration from a non-SSA based patching to an SSA based one can be problematic. Make sure you test the transition when you migrate from older version of the frameworks. 
To continue to use a non-SSA based on, set [ConfigurationService.useSSAToPatchPrimaryResource](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L462) to `false`.

See some identified problematic migration cases and how to handle them in [StatusPatchSSAMigrationIT](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuspatchnonlocking/StatusPatchSSAMigrationIT.java).

TODO using new instance to update status always,

### Event Sources related changes

#### Multi-cluster support in InformerEventSource

`InformerEventSource` now supports watching remote clusters. You can simply pass an `KubernetesClient` that is
initialized to connect to a different cluster where the controller runs. See [InformerEventSourceConfiguration.withKubernetesClient](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/InformerEventSourceConfiguration.java)

Such an informer behaves exactly as a normal one. Obviously, owner references won't work, so you have to specify a `SecondaryToPrimaryMapper` (probably based on labels or annotations).

See related integration test [here](https://github.com/operator-framework/java-operator-sdk/tree/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/informerremotecluster)

#### SecondaryToPrimaryMapper now checks resource types

The owner reference based mappers are now checking the type (`kind` and `apiVersion`) of the resource when resolving the mapping. This is important
since a resource may have owner references to a different resource type with the same name.

See implementation details [here](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/Mappers.java#L74-L75)

#### InformerEventSource-related reactors

There are multiple smaller changes to `InformerEventSource` and related classes:

1. `InformerConfiguration` is renamed to [`InformerEventSourceConfiguration'](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/InformerEventSourceConfiguration.java)
2. `InformerEventSourceConfiguration` doesn't require `EventSourceContext` to be initialized anymore.
 
#### All EventSource is now a ResourceEventSource

The [`EventSource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java) abstraction is now always aware of the resources and
handles accessing (the cached) resources, filtering, and additional capabilities. Before v5, such capabilities were present only in a sub-class called `ResourceEventSource`,
but we decided to merge and remove `ResourceEventSource` since this has a nice impact on other parts of the system in terms of architecture. 

If you still need to create an `EventSource` that does only the triggering, see [TimerEventSource](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/timer/TimerEventSource.java) as an example. 

#### Naming event sources

The `name` is now directly property of the [`EventSource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java#L45).

This results in nicer internal structures. For example, if a DependentResource provides an EventSource, we have more options to set the name for it.

### ControllerConfiguration annotation related changes

You no longer have to annotate the reconciler with `@ControllerConfiuraion` annotation. 
This annotation is (one) way to override the default properties of a controller.
If the annotation is not present, the default values from the annotation are used.

PR: https://github.com/operator-framework/java-operator-sdk/pull/2203

In addition to that, the informer-related configurations are now extracted into
a separate [`@Informer`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/Informer.java) annotation within [`@ControllerConfiguration`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java#L24). Hopefully this makes the underlying components more explicit
and easier to understand. Note that the same `@Informer` annotation is used when configuring a managed `KubernetesDependentResource` with
[`KubernetesDependent`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/KubernetesDependent.java#L33) annotation.


### EventSourceInitializer and ErrorStatusHandler are removed

Both the `EventSourceIntializer` and `ErrorStatusHandler` interfaces are removed, and their methods are moved directly 
under [`Reconciler`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java#L30-L56).

If possible, we try to avoid such marker interfaces since it is hard to deduce related usage just by looking at the source code. 
You can now simply override those methods when implementing the `Reconciler` interface.

### Cloning accessing secondary resources

When accessing the secondary resources using [`Context.getSecondaryResource(s)(...)`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Context.java#L19-L29), the resources are no longer cloned by default, since 
cloning could have an impact on performance. Note that means that these POJOs should be used only for "read-only"; any changes
are now made directly to the cached resource. This should be avoided since the same resource instance may be present for other reconciliation cycles and would
no longer represent the state on the server.

If you want to still clone resource by default, set [ConfigurationService.cloneSecondaryResourcesWhenGettingFromCache](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L484) to `true`.


### Remove automated observed generation handling

The automatic observed generation handling feature was removed since it is trivial to implement inside the reconciler, but it made
the implementation much more complex, especially if the framework would have to support it both for served side apply and client side apply.

You can check a sample implementation how to do it manually in this [integration test](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/manualobservedgeneration/).

## Dependent Resource related changes

### ResourceDescriminator is removal and related changes

The primary reason `ResourceDiscriminator` was introduced is to cover the case when there are
more dependent resources for that same type, so there was a need a generic mechanism to
associate the resources from API served with the related dependent resource. 
This mechanism is now improved with a more lightweight approach, that made the `ResourceDiscriminator`
obsolete. 

As a replacement, the dependent resource will select the target resource based on the desired state. 
See the generic implementation in [`AbstractDependentResource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/AbstractDependentResource.java#L135-L144).
Calculating the desired state can be costly and might depend on other resources. For `KubernetesDependentResource` 
is usually enough to provide the name and namespace (if namespace-scoped) of the target resource, therefore 
in case the desired state is more heavy weight, in order to provide the ID of the target resource you might
override [`ResourceID managedSecondaryResourceID()`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/KubernetesDependentResource.java#L234-L244) method.

TODO add sample.

### Read-only bulk dependent resources

Read-only bulk dependent resources are now supported; this was a request from multiple users, but it required changes to the underlying APIs.
Please check the documentation for further details.

See also the related [integration test](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/bulkdependent/readonly).


### Multiple Dependents with Activation Condition

Until now, activation conditions had a limitation that only one condition was allowed for a specific resource type. 
For example, two ConfigMap dependent resources were not allowed, both with activation conditions. The underlying issue
was with the informer registration process. When an activation condition is evaluated as "met" in the background,
the informer is registered dynamically for the target resource type. However, we need to avoid registering multiple
informers of the same kind. To prevent this the dependent resource must specify the [name of the informer](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/multipledependentwithactivation/ConfigMapDependentResource2.java#L12).

See the complete example [here](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/multipledependentwithactivation).


### The getSecondaryResource() is Activation condition aware 

When there is an activation condition for a resource type, it might or might not be met based that 
an informer might be registered for the target kind. However, when calling `Context.getSecondaryResource` 
and its alternatives; it behaves differently if there is an Informer registered or not. Thus, normally
throws an exception if there is no registered informer for the target type. For resources
with activation condition, this might be confusing however. Therefore, if a dependent resource for a type with an activation
condition is present, it always behaves as there is an Informer registered.

See related [issue](https://github.com/operator-framework/java-operator-sdk/issues/2198) for details.

## Workflow related changes

### Explicit workflow invocation

You can explicitly invoke managed workflows during reconciliation and/or cleanup, until now the execution always happened 
before the `reconcile(...)` (respectively before `cleanup(...)`). This mean you can do all kind of operations - typically validations -
before executing the workflow. Sample:

```java
@Workflow(explicitInvocation = true,
    dependents = @Dependent(type = ConfigMapDependent.class))
@ControllerConfiguration
public class WorkflowExplicitCleanupReconciler
    implements Reconciler<WorkflowExplicitCleanupCustomResource>,
    Cleaner<WorkflowExplicitCleanupCustomResource> {

  @Override
  public UpdateControl<WorkflowExplicitCleanupCustomResource> reconcile(
      WorkflowExplicitCleanupCustomResource resource,
      Context<WorkflowExplicitCleanupCustomResource> context) {

    context.managedWorkflowAndDependentResourceContext().reconcileManagedWorkflow();

    return UpdateControl.noUpdate();
  }

  @Override
  public DeleteControl cleanup(WorkflowExplicitCleanupCustomResource resource,
      Context<WorkflowExplicitCleanupCustomResource> context) {

    context.managedWorkflowAndDependentResourceContext().cleanupManageWorkflow();
    // this can be checked
    // context.managedWorkflowAndDependentResourceContext().getWorkflowCleanupResult()
    return DeleteControl.defaultDelete();
  }
}
```

To turn on this mode of execution, set [`explicitInvocation`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Workflow.java#L26) flag to true in managed workflow definition.

See the following integration tests for [invocation](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowexplicitinvocation) and [cleanup](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowexplicitcleanup).

### Explicit exception handling

If an exception happens during reconciliation of a workflow, the framework automatically throws it further. 
You can now set [`handleExceptionsInReconciler`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Workflow.java#L40) to true for a workflow and check the thrown exceptions explicitly 
in the execution results.

```java
@Workflow(handleExceptionsInReconciler = true,
    dependents = @Dependent(type = ConfigMapDependent.class))
@ControllerConfiguration
public class HandleWorkflowExceptionsInReconcilerReconciler
    implements Reconciler<HandleWorkflowExceptionsInReconcilerCustomResource>,
    Cleaner<HandleWorkflowExceptionsInReconcilerCustomResource> {

  private volatile boolean errorsFoundInReconcilerResult = false;
  private volatile boolean errorsFoundInCleanupResult = false;

  @Override
  public UpdateControl<HandleWorkflowExceptionsInReconcilerCustomResource> reconcile(
      HandleWorkflowExceptionsInReconcilerCustomResource resource,
      Context<HandleWorkflowExceptionsInReconcilerCustomResource> context) {

    errorsFoundInReconcilerResult = context.managedWorkflowAndDependentResourceContext()
        .getWorkflowReconcileResult().erroredDependentsExist();

    // check errors here:
    Map<DependentResource, Exception> errors = context.getErroredDependents();

    return UpdateControl.noUpdate();
  }
```

See integration test [here](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowsilentexceptionhandling).

### @Workflow annotation

The managed workflow definition is now a separate `@Workflow` annotation; it is no longer part of `@ControllerConfiguration`.

See sample usage [here](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageManagedDependentsReconciler.java#L14-L20)

### CRDPresentActivationCondition

Activation conditions are typically used to check if the cluster has specific capabilities (e.g., is cert-manager available).
Such a check can be done by verifying if a particular custom resource definition (CRD) is present on the cluster. You
can now use the generic [`CRDPresentActivationCondition`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/workflow/CRDPresentActivationCondition.java) for this
purpose, it will check if the CRD of a target resource type of a dependent resource exists on the cluster.

See usage in integration test [here](https://github.com/operator-framework/java-operator-sdk/blob/refs/heads/next/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/crdpresentactivation).

## Experimental

### Check if the following reconciliation is imminent

You can now check if the subsequent reconciliation will happen right after the current one. In other words 
If we have already received a new event triggering the reconciliation for the actual resource. 
This information is available from the [context](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Context.java#L69).

Note that this could be useful, for example, in situations when a heavy task would be repeated in the follow-up reconciliation. In the current
reconciliation, you can check this flag and return, don't do the heavy task twice. Note that this is a semi-experimental feature, so please let us know
if you found this helpful.

```java
@Override
public UpdateControl<NextReconciliationImminentCustomResource> reconcile(MyCustomResource resource, Context<MyCustomResource> context) {

  if (context.isNextReconciliationImminent()) {
    // your logic, maybe return?
  }
}
```

See related [integration test](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/nextreconciliationimminent).


