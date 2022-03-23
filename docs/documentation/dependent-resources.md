---
title: Dependent Resources Feature 
description: Dependent Resources Feature 
layout: docs 
permalink: /docs/dependent-resources
---

# Dependent Resources

DISCLAIMER: The Dependent Resource support is relatively new feature, while we strove to cover what we
anticipate will be the most common use cases, the implementation is not simple and might still
evolve. As a result, some APIs could be a subject of change in the future. However,
non-backwards compatible changes are expected to be trivial to migrate to.

## Motivations and Goals

Most operators need to deal with secondary resources when trying to realize the desired state
described by the primary resource it is in charge of. For example, the Kubernetes-native
`Deployment` controller needs to manage `ReplicaSet` instances as part of a `Deployment`'s
reconciliation process. In this instance, `ReplicatSet` is considered a secondary resource for
the `Deployment` controller.

Controllers that deal with secondary resources typically need to perform the following steps, for
each secondary resource:


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

While these steps are not difficult in and of themselves, there are some subtleties that can lead to
bugs or sub-optimal code if not done right. As this process is pretty much similar for each
dependent resource, it makes sense for the SDK to offer some level of support to remove the
boilerplate code of these repetitive actions. It should be possible to handle common cases (such as
dealing with Kubernetes-native secondary resources) in a semi-declarative way with only a minimal
amount of code, JOSDK taking care of wiring everything accordingly.

Moreover, in order for your reconciler to get informed of events on these secondary resources, you
need to configure and create event sources and maintain them. JOSDK already makes it rather easy to
deal with these, but dependent resources makes it even simpler.

Finally, there are also opportunities for the SDK to transparently add features that are even
trickier to get right, such as immediate caching of updated or created resources (so that your
reconciler doesn't need to wait for a cluster roundtrip to continue its work) and associated event
filtering (so that something your reconciler just changed doesn't re-trigger a reconciliation, for
example).

## Design

### `DependentResource` vs. `AbstractDependentResource`

The new `DependentResource` interface lies at the core of the design and strives to encapsulate the
logic that is required to reconcile the state of the associated secondary resource based on the
state of the primary one. For most cases, this logic will follow the flow expressed above and JOSDK
provides a very convenient implementation of this logic in the form of the
`AbstractDependentResource` class. If your logic doesn't fit this pattern, though, you can still
provide your own `reconcile` method implementation. While the benefits of using dependent resources
are less obvious in that case, this allows you to separate the logic necessary to deal with each
secondary resource in its own class that can then be tested in isolation via unit tests. You can
also use the declarative support with your own implementations as we shall see later on.

`AbstractDependentResource` is designed so that classes extending it specify which functionality
they support by implementing trait interfaces. This design has been selected to express the fact
that not all secondary resources are completely under the control of the primary reconciler: some
dependent resources are only ever created or updated for example and we needed a way to let JOSDK
know when that is the case. We therefore provide trait interfaces: `Creator`,
`Updater` and `Deleter` to express that the `DependentResource` implementation will provide custom
functionality to create, update and delete its associated secondary resources, respectively. If
these traits are not implemented then parts of the logic described above is never triggered: if your
implementation doesn't implement `Creator`, for example,
`AbstractDependentResource` will never try to create the associated secondary resource, even if it
doesn't exist. It is possible to not implement any of these traits and therefore create read-only dependent resources that will trigger your
reconciler whenever a user interacts with them but that are never modified by your reconciler
itself.

### Batteries included: convenient `DependentResource` implementations!

JOSDK also offers several other convenient implementations building on top of
`AbstractDependentResource` that you can use as starting points for your own implementations.

One such implementation is the `KubernetesDependentResource` class that makes it really easy to work
with Kubernetes-native resources. In this case, you usually only need to provide an
implementation for the `desired` method to tell JOSDK what the desired state of your secondary
resource should be based on the specified primary resource state. JOSDK takes care of everything
else using default implementations that you can override in case you need more precise control of
what's going on.

We also provide implementations that makes it very easy to cache
(`AbstractCachingDependentResource`) or make it easy to poll for changes in external
resources (`PollingDependentResource`, `PerResourcePollingDependentResource`). All the provided
implementations can be found in the `io/javaoperatorsdk/operator/processing/dependent` package of
the `operator-framework-core` module.

## Managed Dependent Resources

As mentioned previously, one goal of this implementation is to make it possible to
semi-declaratively create and wire dependent resources. You can annotate your reconciler with
`@Dependent` annotations that specify which `DependentResource` implementation it depends upon.
JOSDK will take the appropriate steps to wire everything together and call your
`DependentResource` implementations `reconcile` method before your primary resource is reconciled.
This makes sense in most use cases where the logic associated with the primary resource is usually
limited to status handling based on the state of the secondary resources. This behavior and
automated handling is referred to as "managed" because the `DependentResource`
implementations are managed by JOSDK. See [related sample](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageManagedDependentsReconciler.java):

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
        simulateErrorIfRequested(webPage);

        final var name = context.getSecondaryResource(ConfigMap.class).orElseThrow()
                .getMetadata().getName();
        webPage.setStatus(createStatus(name));
        return UpdateControl.updateStatus(webPage);
    }
    
}
```

## Standalone Dependent Resources

To use dependent resources in more complex workflows, when the reconciliation requires additional logic, the standalone
mode is available. In practice this means that the developer is responsible to initializing and managing and 
calling reconcile method. 




## Other Dependent Resources features