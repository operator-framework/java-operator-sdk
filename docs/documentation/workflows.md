---
title: Workflows
description: Reference Documentation for Workflows
layout: docs
permalink: /docs/workflows
---

## Overview

Kubernetes (k8s) does not have the notion of a resource "depending on" on another k8s resource,
at least not in terms of the order in which these resources should be reconciled. Kubernetes
operators typically need to reconcile resources in order because these resources' state often
depends on the state of other resources or cannot be processed until these other resources reach
a given state or some condition holds true for them. Dealing with such scenarios are therefore
rather common for operators and the purpose of the workflow feature of the Java Operator SDK
(JOSDK) is to simplify supporting such cases in a declarative way. Workflows build on top of the
[dependent resources](https://javaoperatorsdk.io/docs/dependent-resources) feature.
While dependent resources focus on how a given secondary resource should be reconciled,
workflows focus on orchestrating how these dependent resources should be reconciled.

Workflows describe how as a set of
[dependent resources](https://javaoperatorsdk.io/docs/dependent-resources) (DR) depend on one
another, along with the conditions that need to hold true at certain stages of the
reconciliation process.

## Elements of Workflow

- **Dependent resource** (DR) - are the resources being managed in a given reconciliation logic.
- **Depends-on relation** - a `B` DR depends on another `A` DR if `B` needs to be reconciled
  after `A`.
- **Reconcile precondition** - is a condition on a given DR that needs to be become true before the
  DR is reconciled. This also allows to define optional resources that would, for example, only be
  created if a flag in a custom resource `.spec` has some specific value.
- **Ready postcondition** - is a condition on a given DR to prevent the workflow from
  proceeding until the condition checking whether the DR is ready holds true
- **Delete postcondition** - is a condition on a given DR to check if the reconciliation of
  dependents can proceed after the DR is supposed to have been deleted

## Defining Workflows

Similarly to dependent resources, there are two ways to define workflows, in managed and standalone
manner.

### Managed

Annotations can be used to declaratively define a workflow for a `Reconciler`. Similarly to how
things are done for dependent resources, managed workflows execute before the `reconcile` method
is called. The result of the reconciliation can be accessed via the `Context` object that is
passed to the `reconcile` method.

The following sample shows a hypothetical use case to showcase all the elements: the primary
`TestCustomResource` resource handled by our `Reconciler` defines two dependent resources, a
`Deployment` and a `ConfigMap`. The `ConfigMap` depends on the `Deployment` so will be
reconciled after it. Moreover, the `Deployment` dependent resource defines a ready
post-condition, meaning that the `ConfigMap` will not be reconciled until the condition defined
by the `Deployment` becomes `true`. Additionally, the `ConfigMap` dependent also defines a
reconcile pre-condition, so it also won't be reconciled until that condition becomes `true`. The
`ConfigMap` also defines a delete post-condition, which means that the workflow implementation
will only consider the `ConfigMap` deleted until that post-condition becomes `true`.

```java

@ControllerConfiguration(dependents = {
    @Dependent(name = DEPLOYMENT_NAME, type = DeploymentDependentResource.class,
        readyPostcondition = DeploymentReadyCondition.class),
    @Dependent(type = ConfigMapDependentResource.class,
        reconcilePrecondition = ConfigMapReconcileCondition.class,
        deletePostcondition = ConfigMapDeletePostCondition.class,
        dependsOn = DEPLOYMENT_NAME)
})
public class SampleWorkflowReconciler implements Reconciler<TestCustomResource>,
    Cleaner<WorkflowAllFeatureCustomResource> {

  public static final String DEPLOYMENT_NAME = "deployment";

  @Override
  public UpdateControl<WorkflowAllFeatureCustomResource> reconcile(
      WorkflowAllFeatureCustomResource resource,
      Context<WorkflowAllFeatureCustomResource> context) {

    resource.getStatus()
        .setReady(
            context.managedDependentResourceContext()  // accessing workflow reconciliation results
                .getWorkflowReconcileResult().orElseThrow()
                .allDependentResourcesReady());
    return UpdateControl.patchStatus(resource);
  }

  @Override
  public DeleteControl cleanup(WorkflowAllFeatureCustomResource resource,
      Context<WorkflowAllFeatureCustomResource> context) {
    // emitted code

    return DeleteControl.defaultDelete();
  }
}

```

### Standalone

In this mode workflow is built manually
using [standalone dependent resources](https://javaoperatorsdk.io/docs/dependent-resources#standalone-dependent-resources)
. The workflow is created using a builder, that is explicitly called in the reconciler (from web
page sample):

```java

@ControllerConfiguration(
    labelSelector = WebPageDependentsWorkflowReconciler.DEPENDENT_RESOURCE_LABEL_SELECTOR)
public class WebPageDependentsWorkflowReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

  public static final String DEPENDENT_RESOURCE_LABEL_SELECTOR = "!low-level";
  private static final Logger log =
      LoggerFactory.getLogger(WebPageDependentsWorkflowReconciler.class);

  private KubernetesDependentResource<ConfigMap, WebPage> configMapDR;
  private KubernetesDependentResource<Deployment, WebPage> deploymentDR;
  private KubernetesDependentResource<Service, WebPage> serviceDR;
  private KubernetesDependentResource<Ingress, WebPage> ingressDR;

  private Workflow<WebPage> workflow;

  public WebPageDependentsWorkflowReconciler(KubernetesClient kubernetesClient) {
    initDependentResources(kubernetesClient);
    workflow = new WorkflowBuilder<WebPage>()
        .addDependentResource(configMapDR)
        .addDependentResource(deploymentDR)
        .addDependentResource(serviceDR)
        .addDependentResource(ingressDR).withReconcilePrecondition(new ExposedIngressCondition())
        .build();
  }

  @Override
  public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
    return EventSourceInitializer.nameEventSources(
        configMapDR.initEventSource(context),
        deploymentDR.initEventSource(context),
        serviceDR.initEventSource(context),
        ingressDR.initEventSource(context));
  }

  @Override
  public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {

    var result = workflow.reconcile(webPage, context);

    webPage.setStatus(createStatus(result));
    return UpdateControl.patchStatus(webPage);
  }
  // omitted code
}

```

## Workflow Execution

This section describes how a workflow is executed in details, how the ordering is determined and
how conditions and errors affect the behavior. The workflow execution is divided in two parts
similarly to how `Reconciler` and `Cleaner` behavior are separated.
[Cleanup](https://javaoperatorsdk.io/docs/features#the-reconcile-and-cleanup) is
executed if a resource is marked for deletion.

## Common Principles

- **As complete as possible execution** - when a workflow is reconciled, it tries to reconcile as
  many resources as possible. Thus if an error happens or a ready condition is not met for a
  resources, all the other independent resources will be still reconciled. This is the opposite
  to a fail-fast approach. The assumption is that eventually in this way the overall state will
  converge faster towards the desired state than would be the case if the reconciliation was
  aborted as soon as an error occurred.
- **Concurrent reconciliation of independent resources** - the resources which doesn't depend on
  others are processed concurrently. The level of concurrency is customizable, could be set to
  one if required. By default, workflows use the executor service
  from [ConfigurationService](https://github.com/java-operator-sdk/java-operator-sdk/blob/6f2a252952d3a91f6b0c3c38e5e6cc28f7c0f7b3/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L120-L120)

## Reconciliation

This section describes how a workflow is executed, considering first which rules apply, then
demonstrated using examples:

### Rules

1. A workflow is a Directed Acyclic Graph (DAG) build from the DRs and their associated
   `depends-on` relations.
2. Root nodes, i.e. nodes in the graph that do not depend on other nodes are reconciled first,
   in a parallel manner.
2. A DR is reconciled if it does not depend on any other DRs, or *ALL* the DRs it depends on are
   reconciled and ready. If a DR defines a reconcile pre-condition, then this condition must
   become `true` before the DR is reconciled.
2. A DR is considered *ready* if it got successfully reconciled and any ready post-condition it
   might define is `true`.
3. If a DR's reconcile pre-condition is not met, this DR is deleted. All of the DRs that depend
   on the dependent resource being considered are also recursively deleted. This implies that
   DRs are deleted in reverse order compared the one in which they are reconciled. The reason
   for this behavior is (Will make a more detailed blog post about the design decision, much deeper
   than the reference documentation)
   The reasoning behind this behavior is as follows: a DR with a reconcile pre-condition is only
   reconciled if the condition holds `true`. This means that if the condition is `false` and the
   resource didn't exist already, then the associated resource would not be created. To ensure
   idempotency (i.e. with the same input state, we should have the same output state), from this
   follows that if the condition doesn't hold `true` anymore, the associated resource needs to
   be deleted because the resource shouldn't exist/have been created.
4. For a DR to be deleted by a workflow, it needs to implement the `Deleter` interface, in which
   case its `delete` method will be called, unless it also implements the `GarbageCollected`
   interface. If a DR doesn't implement `Deleter` it is considered as automatically deleted. If
   a delete post-condition exists for this DR, it needs to become `true` for the workflow to
   consider the DR as successfully deleted.

### Samples

Notation: The arrows depicts reconciliation ordering, thus following the reverse direction of the  
`depends-on` relation:
`1 --> 2` mean `DR 2` depends-on `DR 1`.

#### Reconcile Sample

<div class="mermaid" markdown="0"> 

stateDiagram-v2
1 --> 2
1 --> 3
2 --> 4
3 --> 4

</div>

- Root nodes (i.e. nodes that don't depend on any others) are reconciled first. In this example,
  DR `1` is reconciled first since it doesn't depend on others.
  After that both DR `2` and `3` are reconciled concurrently, then DR `4` once both are
  reconciled sucessfully.
- If DR `2` had a ready condition and if it evaluated to as `false`, DR `4` would not be reconciled.
  However `1`,`2` and `3` would be.
- If `1` had a `false` ready condition, neither `2`,`3` or `4` would be reconciled.
- If `2`'s reconciliation resulted in an error, `4` would not be reconciled, but `3`
  would be (and `1` as well, of course).

#### Sample with Reconcile Precondition

<div class="mermaid" markdown="0"> 

stateDiagram-v2
1 --> 2
1 --> 3
3 --> 4
3 --> 5

</div>

- If `3` has a reconcile pre-condition that is not met, `1` and `2` would be reconciled. However,
  DR `3`,`4`,`5` would be deleted: `4` and `5` would be deleted concurrently but `3` would only
  be deleted if `4` and `5` were deleted successfully (i.e. without error) and all existing
  delete post-conditions were met.
- If `5` had a delete post-condition that was `false`, `3` would not be deleted but `4`
  would still be because they don't depend on one another.
- Similarly, if `5`'s deletion resulted in an error, `3` would not be deleted but `4` would be.

## Cleanup

Cleanup works identically as delete for resources in reconciliation in case reconcile pre-condition
is not met, just for the whole workflow.

### Rules

1. Delete is called on a DR if there is no DR that depends on it
2. If a DR has DRs that depend on it, it will only be deleted if all these DRs are successfully
   deleted without error and any delete post-condition is `true`.
3. A DR is "manually" deleted (i.e. it's `Deleter.delete` method is called) if it implements the
   `Deleter` interface but does not implement `GarbageCollected`. If a DR does not implement
   `Deleter` interface, it is considered as deleted automatically.

### Sample

<div class="mermaid" markdown="0"> 

stateDiagram-v2
1 --> 2
1 --> 3
2 --> 4
3 --> 4

</div>

- The DRs are deleted in the following order: `4` is deleted first, then `2` and `3` are deleted
  concurrently, and, only after both are successfully deleted,  `1` is deleted.
- If `2` had a delete post-condition that was `false`, `1` would not be deleted. `4` and `3`
  would be deleted.
- If `2` was in error, DR `1` would not be deleted. DR `4` and `3` would be deleted.
- if `4` was in error, no other DR would be deleted.

## Error Handling

As mentioned before if an error happens during a reconciliation, the reconciliation of other
dependent resources will still happen, assuming they don't depend on the one that failed. If
case multiple DRs fail, the workflow would throw an
['AggregatedOperatorException'](https://github.com/java-operator-sdk/java-operator-sdk/blob/86e5121d56ed4ecb3644f2bc8327166f4f7add72/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/AggregatedOperatorException.java)
containing all the related exceptions.

The exceptions can be handled
by [`ErrorStatusHandler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/14620657fcacc8254bb96b4293eded84c20ba685/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ErrorStatusHandler.java)

## Notes and Caveats

- Delete is almost always called on every resource during the cleanup. However, it might be the case
  that the resources were already deleted in a previous run, or not even created. This should
  not be a problem, since dependent resources usually cache the state of the resource, so are
  already aware that the resource does not exist and that nothing needs to be done if delete is
  called.
- If a resource has owner references, it will be automatically deleted by the Kubernetes garbage
  collector if the owner resource is marked for deletion. This might not be desirable, to make
  sure that delete is handled by the workflow don't use garbage collected kubernetes dependent
  resource, use for
  example [`CRUDNoGCKubernetesDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/86e5121d56ed4ecb3644f2bc8327166f4f7add72/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/CRUDNoGCKubernetesDependentResource.java)
  .
- No state is persisted regarding the workflow execution. Every reconciliation causes all the
  resources to be reconciled again, in other words the whole workflow is again evaluated.

