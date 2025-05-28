---
title: Version 5 Released!
date: 2025-01-06
---

We are excited to announce that Java Operator SDK v5 has been released. This significant effort contains
various features and enhancements accumulated since the last major release and required changes in our APIs.
Within this post, we will go through all the main changes and help you upgrade to this new version, and provide
a rationale behind the changes if necessary.

We will omit descriptions of changes that should only require simple code updates; please do contact
us if you encounter issues anyway.

You can see an introduction and some important changes and rationale behind them from [KubeCon](https://youtu.be/V0NYHt2yjcM?t=1238).

## Various Changes

- From this release, the minimal Java version is 17.
- Various deprecated APIs are removed. Migration should be easy.

## All Changes

You can see all changes [here](https://github.com/operator-framework/java-operator-sdk/compare/v4.9.7...v5.0.0).

## Changes in low-level APIs

### Server Side Apply (SSA)

[Server Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/) is now a first-class citizen in
the framework and
the default approach for patching the status resource. This means that patching a resource or its status through
`UpdateControl` and adding
the finalizer in the background will both use SSA.

Migration from a non-SSA based patching to an SSA based one can be problematic. Make sure you test the transition when
you migrate from older version of the frameworks.
To continue to use a non-SSA based on,
set [ConfigurationService.useSSAToPatchPrimaryResource](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L462)
to `false`.

See some identified problematic migration cases and how to handle them
in [StatusPatchSSAMigrationIT](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/statuspatchnonlocking/StatusPatchSSAMigrationIT.java).

For more detailed description, see our [blog post](../news/nonssa-vs-ssa.md) on SSA.

### Event Sources related changes

#### Multi-cluster support in InformerEventSource

`InformerEventSource` now supports watching remote clusters. You can simply pass a `KubernetesClient` instance
initialized to connect to a different cluster from the one where the controller runs when configuring your event source.
See [InformerEventSourceConfiguration.withKubernetesClient](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/InformerEventSourceConfiguration.java)

Such an informer behaves exactly as a regular one. Owner references won't work in this situation, though, so you have to
specify a `SecondaryToPrimaryMapper` (probably based on labels or annotations).

See related integration
test [here](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/informerremotecluster)

#### SecondaryToPrimaryMapper now checks resource types

The owner reference based mappers are now checking the type (`kind` and `apiVersion`) of the resource when resolving the
mapping. This is important
since a resource may have owner references to a different resource type with the same name.

See implementation
details [here](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/Mappers.java#L74-L75)

#### InformerEventSource-related changes

There are multiple smaller changes to `InformerEventSource` and related classes:

1. `InformerConfiguration` is renamed
   to [
   `InformerEventSourceConfiguration`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/InformerEventSourceConfiguration.java)
2. `InformerEventSourceConfiguration` doesn't require `EventSourceContext` to be initialized anymore.

#### All EventSource are now ResourceEventSources

The [
`EventSource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java)
abstraction is now always aware of the resources and
handles accessing (the cached) resources, filtering, and additional capabilities. Before v5, such capabilities were
present only in a sub-class called `ResourceEventSource`,
but we decided to merge and remove `ResourceEventSource` since this has a nice impact on other parts of the system in
terms of architecture.

If you still need to create an `EventSource` that only supports triggering of your reconciler,
see [
`TimerEventSource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/timer/TimerEventSource.java)
for an example of how this can be accomplished.

#### Naming event sources

[
`EventSource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/EventSource.java#L45)
are now named. This reduces the ambiguity that might have existed when trying to refer to an `EventSource`.

### ControllerConfiguration annotation related changes

You no longer have to annotate the reconciler with `@ControllerConfiguration` annotation.
This annotation is (one) way to override the default properties of a controller.
If the annotation is not present, the default values from the annotation are used.

PR: https://github.com/operator-framework/java-operator-sdk/pull/2203

In addition to that, the informer-related configurations are now extracted into
a separate [
`@Informer`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/Informer.java)
annotation within [
`@ControllerConfiguration`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java#L24).
Hopefully this explicits which part of the configuration affects the informer associated with primary resource.
Similarly, the same `@Informer` annotation is used when configuring the informer associated with a managed
`KubernetesDependentResource` via the
[
`KubernetesDependent`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/KubernetesDependent.java#L33)
annotation.

### EventSourceInitializer and ErrorStatusHandler are removed

Both the `EventSourceInitializer` and `ErrorStatusHandler` interfaces are removed, and their methods moved directly
under [
`Reconciler`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java#L30-L56).

If possible, we try to avoid such marker interfaces since it is hard to deduce related usage just by looking at the
source code.
You can now simply override those methods when implementing the `Reconciler` interface.

### Cloning accessing secondary resources

When accessing the secondary resources using [
`Context.getSecondaryResource(s)(...)`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Context.java#L19-L29),
the resources are no longer cloned by default, since
cloning could have an impact on performance. This means that you now need to ensure that these any changes
are now made directly to the underlying cached resource. This should be avoided since the same resource instance may be
present for other reconciliation cycles and would
no longer represent the state on the server.

If you want to still clone resources by default,
set [
`ConfigurationService.cloneSecondaryResourcesWhenGettingFromCache`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L484)
to `true`.

### Removed automated observed generation handling

The automatic observed generation handling feature was removed since it is easy to implement inside the reconciler, but
it made
the implementation much more complex, especially if the framework would have to support it both for served side apply
and client side apply.

You can check a sample implementation how to do it manually in
this [integration test](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/manualobservedgeneration/).

## Dependent Resource related changes

### ResourceDiscriminator is removed and related changes

The primary reason `ResourceDiscriminator` was introduced was to cover the case when there are
more than one dependent resources of a given type associated with a given primary resource. In this situation, JOSDK
needed a generic mechanism to
identify which resources on the cluster should be associated with which dependent resource implementation.
We improved this association mechanism, thus rendering `ResourceDiscriminator` obsolete.

As a replacement, the dependent resource will select the target resource based on the desired state.
See the generic implementation in [
`AbstractDependentResource`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/AbstractDependentResource.java#L135-L144).
Calculating the desired state can be costly and might depend on other resources. For `KubernetesDependentResource`
it is usually enough to provide the name and namespace (if namespace-scoped) of the target resource, which is what the
`KubernetesDependentResource` implementation does by default. If you can determine which secondary to target without
computing the desired state via its associated `ResourceID`, then we encourage you to override the
[
`ResourceID targetSecondaryResourceID()`](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/KubernetesDependentResource.java#L234-L244)
method as shown
in [this example](https://github.com/operator-framework/java-operator-sdk/blob/c7901303c5304e6017d050f05cbb3d4930bdfe44/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/multipledrsametypenodiscriminator/MultipleManagedDependentNoDiscriminatorConfigMap1.java#L24-L35)

### Read-only bulk dependent resources

Read-only bulk dependent resources are now supported; this was a request from multiple users, but it required changes to
the underlying APIs.
Please check the documentation for further details.

See also the
related [integration test](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/dependent/bulkdependent/readonly).

### Multiple Dependents with Activation Condition

Until now, activation conditions had a limitation that only one condition was allowed for a specific resource type.
For example, two `ConfigMap` dependent resources were not allowed, both with activation conditions. The underlying issue
was with the informer registration process. When an activation condition is evaluated as "met" in the background,
the informer is registered dynamically for the target resource type. However, we need to avoid registering multiple
informers of the same kind. To prevent this the dependent resource must specify
the [name of the informer](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/multipledependentwithactivation/ConfigMapDependentResource2.java#L12).

See the complete
example [here](https://github.com/operator-framework/java-operator-sdk/blob/1635c9ea338f8e89bacc547808d2b409de8734cf/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/multipledependentwithactivation).

### `getSecondaryResource` is Activation condition aware

When an activation condition for a resource type is not met, no associated informer might be registered for that
resource type. However, in this situation, calling `Context.getSecondaryResource`
and its alternatives would previously throw an exception. This was, however, rather confusing and a better user
experience would be to return an empty value instead of throwing an error. We changed this behavior in v5 to make it
more user-friendly and attempting to retrieve a secondary resource that is gated by an activation condition will now
return an empty value as if the associated informer existed.

See related [issue](https://github.com/operator-framework/java-operator-sdk/issues/2198) for details.

## Workflow related changes

### `@Workflow` annotation

The managed workflow definition is now a separate `@Workflow` annotation; it is no longer part of
`@ControllerConfiguration`.

See sample
usage [here](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageManagedDependentsReconciler.java#L14-L20)

### Explicit workflow invocation

Before v5, the managed dependents part of a workflow would always be reconciled before the primary `Reconciler`
`reconcile` or `cleanup` methods were called. It is now possible to explictly ask for a workflow reconciliation in your
primary `Reconciler`, thus allowing you to control when the workflow is reconciled. This mean you can perform all kind
of operations - typically validations - before executing the workflow, as shown in the sample below:

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

To turn on this mode of execution, set [
`explicitInvocation`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Workflow.java#L26)
flag to `true` in the managed workflow definition.

See the following integration tests
for [
`invocation`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowexplicitinvocation)
and [
`cleanup`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowexplicitcleanup).

### Explicit exception handling

If an exception happens during a workflow reconciliation, the framework automatically throws it further.
You can now set [
`handleExceptionsInReconciler`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Workflow.java#L40)
to true for a workflow and check the thrown exceptions explicitly
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
}
```

See integration
test [here](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/workflowsilentexceptionhandling).

### CRDPresentActivationCondition

Activation conditions are typically used to check if the cluster has specific capabilities (e.g., is cert-manager
available).
Such a check can be done by verifying if a particular custom resource definition (CRD) is present on the cluster. You
can now use the generic [
`CRDPresentActivationCondition`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/workflow/CRDPresentActivationCondition.java)
for this
purpose, it will check if the CRD of a target resource type of a dependent resource exists on the cluster.

See usage in integration
test [here](https://github.com/operator-framework/java-operator-sdk/blob/refs/heads/next/operator-framework/src/test/java/io/javaoperatorsdk/operator/workflow/crdpresentactivation).

## Fabric8 client updated to 7.0

The Fabric8 client has been updated to version 7.0.0. This is a new major version which implies that some API might have
changed. Please take a look at the [Fabric8 client 7.0.0 migration guide](https://github.com/fabric8io/kubernetes-client/blob/main/doc/MIGRATION-v7.md).

### CRD generator changes

Starting with v5.0 (in accordance with changes made to the Fabric8 client in version 7.0.0), the CRD generator will use the maven plugin instead of the annotation processor as was previously the case.
In many instances, you can simply configure the plugin by adding the following stanza to your project's POM build configuration:

```xml
<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>crd-generator-maven-plugin</artifactId>
    <version>${fabric8-client.version}</version>
    <executions>
      <execution>
        <goals>
          <goal>generate</goal>
        </goals>
      </execution>
    </executions>
</plugin>

```
*NOTE*: If you use the SDK's JUnit extension for your tests, you might also need to configure the CRD generator plugin to access your test `CustomResource` implementations as follows:
```xml

<plugin>
    <groupId>io.fabric8</groupId>
    <artifactId>crd-generator-maven-plugin</artifactId>
    <version>${fabric8-client.version}</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <phase>process-test-classes</phase>
            <configuration>
                <classesToScan>${project.build.testOutputDirectory}</classesToScan>
                <classpath>WITH_ALL_DEPENDENCIES_AND_TESTS</classpath>
            </configuration>
        </execution>
    </executions>
</plugin>

```

Please refer to the [CRD generator documentation](https://github.com/fabric8io/kubernetes-client/blob/main/doc/CRD-generator.md) for more details.


## Experimental

### Check if the following reconciliation is imminent

You can now check if the subsequent reconciliation will happen right after the current one because the SDK has already
received an event that will trigger a new reconciliation
This information is available from
the [
`Context`](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Context.java#L69).

Note that this could be useful, for example, in situations when a heavy task would be repeated in the follow-up
reconciliation. In the current
reconciliation, you can check this flag and return to avoid unneeded processing. Note that this is a semi-experimental
feature, so please let us know
if you found this helpful.

```java

@Override
public UpdateControl<NextReconciliationImminentCustomResource> reconcile(MyCustomResource resource, Context<MyCustomResource> context) {

    if (context.isNextReconciliationImminent()) {
        // your logic, maybe return?
    }
}
```

See
related [integration test](https://github.com/operator-framework/java-operator-sdk/blob/664cb7109fe62f9822997d578ae7f57f17ef8c26/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/nextreconciliationimminent).