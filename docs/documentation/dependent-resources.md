---
title: Dependent Resources Feature 
description: Dependent Resources Feature 
layout: docs 
permalink: /docs/dependent-resources
---

# Dependent Resources

DISCLAIMER: The Dependent Resource support is relatively new feature, while we strove to cover what we anticipate will
be the most common use cases, the implementation is not simple and might still evolve. As a result, some APIs could be a
subject of change in the future. However, non-backwards compatible changes are expected to be trivial to migrate to.

## Motivations and Goals

Most operators need to deal with secondary resources when trying to realize the desired state described by the primary
resource it is in charge of. For example, the Kubernetes-native
`Deployment` controller needs to manage `ReplicaSet` instances as part of a `Deployment`'s reconciliation process. In
this instance, `ReplicatSet` is considered a secondary resource for the `Deployment` controller.

Controllers that deal with secondary resources typically need to perform the following steps, for each secondary
resource:


<div class="mermaid" markdown="0"> 
flowchart TD

compute[Compute desired secondary resource based on primary state] --> A
A{Secondary resource exists?}
A -- Yes --> match
A -- No --> Create --> Done

match{Matches desired state?}
match -- Yes --> Done
match -- No --> Update --> Done

</div>

While these steps are not difficult in and of themselves, there are some subtleties that can lead to bugs or sub-optimal
code if not done right. As this process is pretty much similar for each dependent resource, it makes sense for the SDK
to offer some level of support to remove the boilerplate code of these repetitive actions. It should be possible to
handle common cases (such as dealing with Kubernetes-native secondary resources) in a semi-declarative way with only a
minimal amount of code, JOSDK taking care of wiring everything accordingly.

Moreover, in order for your reconciler to get informed of events on these secondary resources, you need to configure and
create event sources and maintain them. JOSDK already makes it rather easy to deal with these, but dependent resources
makes it even simpler.

Finally, there are also opportunities for the SDK to transparently add features that are even trickier to get right,
such as immediate caching of updated or created resources (so that your reconciler doesn't need to wait for a cluster
roundtrip to continue its work) and associated event filtering (so that something your reconciler just changed doesn't
re-trigger a reconciliation, for example).

## Design

### `DependentResource` vs. `AbstractDependentResource`

The
new [`DependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/DependentResource.java)interface
lies at the core of the design and strives to encapsulate the logic that is required to reconcile the state of the
associated secondary resource based on the state of the primary one. For most cases, this logic will follow the flow
expressed above and JOSDK provides a very convenient implementation of this logic in the form of the
[`AbstractDependentResource`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/AbstractDependentResource.java) 
class. If your logic doesn't fit this pattern, though, you can still provide your
own `reconcile` method implementation. While the benefits of using dependent resources are less obvious in that case,
this allows you to separate the logic necessary to deal with each secondary resource in its own class that can then be
tested in isolation via unit tests. You can also use the declarative support with your own implementations as we shall
see later on.

`AbstractDependentResource` is designed so that classes extending it specify which functionality they support by
implementing trait interfaces. This design has been selected to express the fact that not all secondary resources are
completely under the control of the primary reconciler: some dependent resources are only ever created or updated for
example and we needed a way to let JOSDK know when that is the case. We therefore provide trait interfaces: `Creator`,
`Updater` and `Deleter` to express that the `DependentResource` implementation will provide custom functionality to
create, update and delete its associated secondary resources, respectively. If these traits are not implemented then
parts of the logic described above is never triggered: if your implementation doesn't implement `Creator`, for example,
`AbstractDependentResource` will never try to create the associated secondary resource, even if it doesn't exist. It is
possible to not implement any of these traits and therefore create read-only dependent resources that will trigger your
reconciler whenever a user interacts with them but that are never modified by your reconciler itself.

### Batteries included: convenient DependentResource implementations!

JOSDK also offers several other convenient implementations building on top of
`AbstractDependentResource` that you can use as starting points for your own implementations.

One such implementation is the `KubernetesDependentResource` class that makes it really easy to work with
Kubernetes-native resources. In this case, you usually only need to provide an implementation for the `desired` method
to tell JOSDK what the desired state of your secondary resource should be based on the specified primary resource state.
JOSDK takes care of everything else using default implementations that you can override in case you need more precise
control of what's going on.

We also provide implementations that make it very easy to cache
(`AbstractCachingDependentResource`) or make it easy to poll for changes in external
resources (`PollingDependentResource`, `PerResourcePollingDependentResource`). All the provided implementations can be
found in the `io/javaoperatorsdk/operator/processing/dependent` package of the `operator-framework-core` module.

### Sample Kubernetes Dependent Resource 

A typical use case, when a Kubernetes resource is fully managed - Created, Read, Updated and Deleted (or set to be garbage
collected). The following example shows how to create a `Deployment` dependent resource:

```java
@KubernetesDependent(labelSelector = WebPageManagedDependentsReconciler.SELECTOR)
class DeploymentDependentResource extends CRUKubernetesDependentResource<Deployment, WebPage> {

  public DeploymentDependentResource() {
    super(Deployment.class);
  }

  @Override
  protected Deployment desired(WebPage webPage, Context<WebPage> context) {
    var deploymentName = deploymentName(webPage);
    Deployment deployment = loadYaml(Deployment.class, getClass(), "deployment.yaml");
    deployment.getMetadata().setName(deploymentName);
    deployment.getMetadata().setNamespace(webPage.getMetadata().getNamespace());
    deployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName);

    deployment.getSpec().getTemplate().getMetadata().getLabels()
        .put("app", deploymentName);
    deployment.getSpec().getTemplate().getSpec().getVolumes().get(0)
        .setConfigMap(new ConfigMapVolumeSourceBuilder().withName(configMapName(webPage)).build());
    return deployment;
  }
}
```

The only thing needs to be done is to extend the `CRUKubernetesDependentResource` and specify the desired state.
Note that it is `CRU` instead of `CRUD`, since it not explicitly manages the delete operation. That is handled by
the Kubernetes garbage collector through owner references, what is automatically set to the resource.
`CRUKubernetesDependentResource` is only an adaptor class that already implements the`Creator` and `Updater` but 
not the `Deleter` interface.

See the full source code [here](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/DeploymentDependentResource.java).

## Managed Dependent Resources

As mentioned previously, one goal of this implementation is to make it possible to semi-declaratively create and wire
dependent resources. You can annotate your reconciler with
`@Dependent` annotations that specify which `DependentResource` implementation it depends upon. JOSDK will take the
appropriate steps to wire everything together and call your
`DependentResource` implementations `reconcile` method before your primary resource is reconciled. This makes sense in
most use cases where the logic associated with the primary resource is usually limited to status handling based on the
state of the secondary resources. This behavior and automated handling is referred to as "managed" because
the `DependentResource`
implementations are managed by JOSDK.
See [related sample](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageManagedDependentsReconciler.java):

```java

@ControllerConfiguration(
        labelSelector = SELECTOR,
        dependents = {
                @Dependent(type = ConfigMapDependentResource.class),
                @Dependent(type = DeploymentDependentResource.class),
                @Dependent(type = ServiceDependentResource.class)
        })
public class WebPageManagedDependentsReconciler
        implements Reconciler<WebPage>, ErrorStatusHandler<WebPage> {

    // omitted code

    @Override
    public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
            throws Exception {

        final var name = context.getSecondaryResource(ConfigMap.class).orElseThrow()
                .getMetadata().getName();
        webPage.setStatus(createStatus(name));
        return UpdateControl.updateStatus(webPage);
    }

}
```

See the full source code of
sample [here](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageManagedDependentsReconciler.java)
.

## Standalone Dependent Resources

To use dependent resources in more complex workflows, when the reconciliation requires additional logic, the standalone
mode is available. In practice this means that the developer is responsible to initializing and managing and
calling `reconcile` method. However, this gives possibility for developers to fully customize the workflow for
reconciliation. Like setting conditions (if creation of a resource is desired only in certain situations). Also, for
example if calling an API needs to happen if a service is already up and running
(think configuring a running DB instance).

The following sample is equivalent to the one above with managed dependent resources:

```java

@ControllerConfiguration
public class WebPageStandaloneDependentsReconciler
        implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, EventSourceInitializer<WebPage> {

    private KubernetesDependentResource<ConfigMap, WebPage> configMapDR;
    private KubernetesDependentResource<Deployment, WebPage> deploymentDR;
    private KubernetesDependentResource<Service, WebPage> serviceDR;

    public WebPageStandaloneDependentsReconciler(KubernetesClient kubernetesClient) {
        // 1.
        createDependentResources(kubernetesClient);
    }

    @Override
    public List<EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
        // 2.  
        return List.of(
                configMapDR.initEventSource(context),
                deploymentDR.initEventSource(context),
                serviceDR.initEventSource(context));
    }

    @Override
    public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context)
            throws Exception {

        // 3.  
        configMapDR.reconcile(webPage, context);
        deploymentDR.reconcile(webPage, context);
        serviceDR.reconcile(webPage, context);

        // 4.
        webPage.setStatus(
                createStatus(configMapDR.getResource(webPage).orElseThrow().getMetadata().getName()));
        return UpdateControl.updateStatus(webPage);
    }

    private void createDependentResources(KubernetesClient client) {
        this.configMapDR = new ConfigMapDependentResource();
        this.configMapDR.setKubernetesClient(client);
        configMapDR.configureWith(new KubernetesDependentResourceConfig()
                .setLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR));

        this.deploymentDR = new DeploymentDependentResource();
        deploymentDR.setKubernetesClient(client);
        deploymentDR.configureWith(new KubernetesDependentResourceConfig()
                .setLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR));

        this.serviceDR = new ServiceDependentResource();
        serviceDR.setKubernetesClient(client);
        serviceDR.configureWith(new KubernetesDependentResourceConfig()
                .setLabelSelector(DEPENDENT_RESOURCE_LABEL_SELECTOR));
    }

    // omitted code
}
```

There are multiple things happening here:

1. Dependent resources are explicitly created and can be access later by reference.
2. Event sources are produced by the dependent resources, but needs to be explicitly registered in this case.
3. Reconciliation is called explicitly, but here the workflow customization is fully in the hand of the developer.
4. Status is set in a different way, this is just an alternative way to show, that the actual state can be read using
   the reference. This could be written in a same way as in the managed example.

See the full source code of
sample [here](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageStandaloneDependentsReconciler.java)
.

## Other Dependent Resource features

### Caching and Event Handling in [KubernetesDependentResource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/AbstractDependentResource.java)

1. When a Kubernetes resource is created or updated the related informer (more precisely the `InformerEventSource`),
eventually will receive an event and will cache the up-to-date resource. However, there might be a small time window
when calling the `getResource()` of the dependent resource or getting the resource from the `EventSource` itself won't
return the fresh resource, since it's not received from the Kubernetes API. `KubernetesDependentResource` implementation
makes sure that it or the related `InformerEventSource` always return the up-to-date resource.  

2. Another feature of `KubernetesDependentResource` is to make sure that is a resource is created or updated during
the reconciliation, the later received related event will not trigger the reconciliation again. This is a small
optimization. For example if during a reconciliation a `ConfigMap` is updated using dependent resources, this won't
trigger a new reconciliation. It' does not need to, since the change in the `ConfigMap` is made by the reconciler,
and the fresh version is used further.