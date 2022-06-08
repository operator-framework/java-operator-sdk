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

- **Dependent resource** (DR) - are the resources which are reconciled.
- **Depends-on relation** - if a DR B depends on another DR A, means that B will be reconciled after A is successfully 
  reconciled - and ready if readyPostCondition is used. 
- **Reconcile precondition** - is a condition that needs to be fulfilled before the DR is reconciled. This allows also
  to define optional resources, that for example only created if a flag in a custom resource `.spec` has some 
  specific value.
- **Ready postcondition** - checks if a resource could be considered "ready", typically if pods of a deployment are up
  and running.
- **Delete postcondition** - during the cleanup phase it can be used to check if the resources is successfully deleted,
   so the next resource on which the target resources depends can be deleted as next step.  

## Defining Workflows

Similarly to dependent resources, there are two ways to define workflows, in managed and standalone way.

### Managed

Annotation can be used to declaratively define a workflow for the reconciler. In this case the workflow is executed
before the `reconcile` method is called. The result of the reconciliation is accessed through the `context` object.

Following sample shows a hypothetical sample, where there are two resources a Deployment and a ConfigMap, where 
the ConfigMap depends on the deployment. Deployment has a ready condition so, the config map is only reconciled after
the Deployment and only if that is ready (see ready postcondition). The ConfigMap has a reconcile precondition, there
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
     has a reconcile-precondition that must met too. (Ready means that it is successfully reconciled - without any error - and 
     if it has a ready condition that is met).
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



## Cleanup

## Error Handling

## Notes

- Workflows can be seen as a Directed Acyclic Graph (DAG) - or more precisely a set of DAGs - where nodes are the 
dependent resources and edges are the dependencies.  

[//]: # (ready vs precondition)
[//]: # (issue with garbage collection)
