---
title: Workflows
description: Reference Documentation for Workflows
layout: docs
permalink: /docs/workflows
---

## Overview

Kubernetes (k8s) does not have notion of a resource "depends on" on another k8s resource,
in terms of in what order a set of resources should be reconciled. However, Kubernetes operators are used to manage also
external (non k8s) resources. Typically, when an operator manages a service, after the service is first deployed 
some additional API calls are required to configure it. In this case the configuration step depends
on the service and related resources, in other words the configuration needs to be reconciled after the service is 
up and running. 

The intention behind workflows is to make it easy to describe more complex, almost arbitrary scenarios in a declarative
way. While [dependent resources](https://javaoperatorsdk.io/docs/dependent-resources) describes a logic how a single
resources should be reconciled, workflows describes the process how a set of target resources should be reconciled.

Workflows are defined as a set of [dependent resources](https://javaoperatorsdk.io/docs/dependent-resources) (DR) 
and dependencies between them, along with some conditions that mainly helps define optional resources and 
pre- and post-conditions to describe expected states of a resource at a certain point in the workflow.    

## Elements of Workflow 

- **Dependent resource** (DR) - are the resources which are managed in reconcile logic.
- **Depends-on relation** - if a DR `B` depends on another DR `A`, means that `B` will be reconciled after `A`.
- **Reconcile precondition** - is a condition that needs to be fulfilled before the DR is reconciled. This allows also
  to define optional resources, that for example only created if a flag in a custom resource `.spec` has some 
  specific value.
- **Ready postcondition** - checks if a resource could be considered "ready", typically if pods of a deployment are up
  and running.
- **Delete postcondition** - during the cleanup phase it can be used to check if the resources is successfully deleted,
   so the next resource on which the target resources depends can be deleted as next step.  

## Defining Workflows

Similarly to dependent resources, there are two ways to define workflows, in managed and standalone manner.

### Managed

Annotation can be used to declaratively define a workflow for the reconciler. In this case the workflow is executed
before the `reconcile` method is called. The result of the reconciliation is accessed through the `context` object.

Following sample shows a hypothetical sample, where there are two resources a Deployment and a ConfigMap, where 
the ConfigMap depends on the deployment. Deployment has a ready condition so, the config map is only reconciled after
the Deployment and only if that is ready (see ready postcondition). The ConfigMap reconcile precondition, there
only reconciled if that condition holds. In addition to that contains a delete postCondition, do only considered to be
deleted if that condition holds.

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

In this mode workflow is built manually using [standalone dependent resources](https://javaoperatorsdk.io/docs/dependent-resources#standalone-dependent-resources)
. The workflow is created using a builder, that is explicitly called in the reconciler (from web page sample): 

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
            .addDependent(configMapDR).build()
            .addDependent(deploymentDR).build()
            .addDependent(serviceDR).build()
            .addDependent(ingressDR).withReconcileCondition(new IngressCondition()).build()
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
  // emitted code
}

```

## Workflow Execution 

This section describes how a workflow is executed in details, how is the ordering determined and how condition and
errors effect behavior. The workflow execution as also its API denotes, can be divided to into two parts, 
the reconciliation and cleanup. [Cleanup](https://javaoperatorsdk.io/docs/features#the-reconcile-and-cleanup) is 
executed if a resource is marked for deletion.


## Common Principles

- **As complete as possible execution** - when a workflow is reconciled, it tries to reconcile as many resources as  
  possible. Thus is an error happens or a ready condition is not met for a resources, all the other independent resources  
  will be still reconciled. So this is exactly the opposite of fail-fast approach. The assumption is that in this way 
  the overall desired state is achieved faster than with a fail fast approach.
- **Concurrent reconciliation of independent resources** - the resources which are not dependent on each are processed 
  concurrently. The level of concurrency is customizable, could be set to one if required. By default, workflows use  
  the executor service from [ConfigurationService](https://github.com/java-operator-sdk/java-operator-sdk/blob/6f2a252952d3a91f6b0c3c38e5e6cc28f7c0f7b3/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L120-L120) 

## Reconciliation

This section describes how a workflow is executed, first the rules are defined, then are explained on samples:

### Rules

  1. DR is reconciled if it does not depend on another DR, or ALL the DRs it depends on are ready. In case it
     has a reconcile-precondition that must be met too. (So her ready means that it is successfully reconciled - without
     any error - and if it has a ready condition that is met).
  2. If a reconcile-precondition of a DR is not met, it is deleted. If there are dependent resources which depends on it   
     are deleted first too - this applies recursively. That means that DRs are always deleted in revers order compared    
     how are reconciled.
  3. Delete is called on a dependent resource if as described in point 2. it (possibly transitively) depends on A DR which 
     did not meet it's reconcile condition, and has not DRs depends on it, or if the DR-s which depends on it are 
     successfully deleted. "Delete is called" means, that the dependent resource is checked if it implements `Deleter` interface,
     if implements it but do not implement `GarbageCollected` interface, the `Deleter.delete` method called. If a DR   
     does not implement `Deleter` interface, it is considered as deleted automatically. Successfully deleted means,
     that it is deleted and if a delete-postcondition is present it is met. 
  
### Samples

Notation: The arrows depicts reconciliation ordering, or in depends-on relation in reverse direction: 
`1 --> 2` mean `DR 2` depends-on `DR 1`.   

#### Reconcile Sample

<div class="mermaid" markdown="0"> 

stateDiagram-v2
1 --> 2
1 --> 3
2 --> 4
3 --> 4

</div>

- At the workflow the reconciliation of the nodes would happen in the following way. DR with index `1` is reconciled.
  After DR `2` and `3` is reconciled concurrently, if both finished reconciling, node `4` is reconciled. 
- In case for example `2` would have a ready condition, that would be evaluated as "not met", `4` would not be reconciled.
  However `1`,`2` and `3` would be reconciled. 
- In case `1` would have a ready condition that is not met, neither `2`,`3` or `4` would be reconciled.
- If there would be an error during the reconciliation of `2`, `4` would not be reconciled, but `3` would be 
  (also `1` of course).

#### Sample with Reconcile Precondition

<div class="mermaid" markdown="0"> 

stateDiagram-v2
1 --> 2
1 --> 3
3 --> 4
3 --> 5

</div>

- Considering this sample for case `3` has reconcile-precondition, what is not met. In that case DR `1` and `2` would be
  reconciled. However, DR `3`,`4`,`5` would be deleted in the following way. DR `4` and `5` would be deleted concurrently.
  DR `3` would be deleted if `4` and `5` is deleted successfully, thus no error happened during deletion and all 
  delete-postconditions are met. 
  - If delete-postcondition for `5` would not be met `3` would not be deleted; `4` would be.
  - Similarly, in there would be an error for `5`, `3` would not be deleted, `4` would be. 

## Cleanup

Cleanup works identically as delete for resources in reconciliation in case reconcile-precondition is not met, just for
the whole workflow.

The rule is relatively simple:

Delete is called on a DR if there is no DR that depends on it, or if the DR-s which depends on it are
successfully already deleted. Successfully deleted means, that it is deleted and if a delete-postcondition is present 
it is met. "Delete is called" means, that the dependent resource is checked if it implements `Deleter` interface,
if implements it but do not implement `GarbageCollected` interface, the `Deleter.delete` method called. If a DR   
does not implement `Deleter` interface, it is considered as deleted automatically. 

### Sample

<div class="mermaid" markdown="0"> 

stateDiagram-v2
1 --> 2
1 --> 3
2 --> 4
3 --> 4

</div>

- The DRs are deleted in the following order: `4` is deleted, after `2` and `3` are deleted concurrently, after both
  succeeded `1` is deleted.
- If delete-postcondition would not be met for `2`, node `1` would not be deleted. DR `4` and `3` would be deleted.
- If `2` would be errored, DR `1` would not be deleted. DR `4` and `3` would be deleted.
- if `4` would be errored, no other DR would be deleted.

## Error Handling

As mentioned before if an error happens during a reconciliation, the reconciliation of other dependent resources will
still happen. There might a case that multiple DRs are errored, therefore workflows throws an
['AggregatedOperatorException'](https://github.com/java-operator-sdk/java-operator-sdk/blob/86e5121d56ed4ecb3644f2bc8327166f4f7add72/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/AggregatedOperatorException.java) 
that will contain all the related exceptions. 

The exceptions can be handled by [`ErrorStatusHandler`](https://github.com/java-operator-sdk/java-operator-sdk/blob/86e5121d56ed4ecb3644f2bc8327166f4f7add72/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/AggregatedOperatorException.java)

## Notes and Caveats
 
- If a resource has owner references, it will be automatically deleted by Kubernetes garbage collector if 
  the owner resource is marked for deletion. This might not be desirable, to make sure that delete is handled by the
  workflow don't use garbage collected kubernetes dependent resource, use for example [`CRUDNoGCKubernetesDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/86e5121d56ed4ecb3644f2bc8327166f4f7add72/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/CRUDNoGCKubernetesDependentResource.java).
- After a workflow executed no state is persisted regarding the workflow execution. On every reconciliation
  all the resources are reconciled again, in other words the whole workflow is evaluated again.

