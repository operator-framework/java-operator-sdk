---
title: FAQ
weight: 90
---

### How can I access the events which triggered the Reconciliation?

In the v1.* version events were exposed to `Reconciler` (which was called `ResourceController`
then). This included events (Create, Update) of the custom resource, but also events produced by
Event Sources. After long discussions also with developers of golang version (controller-runtime),
we decided to remove access to these events. We already advocated to not use events in the
reconciliation logic, since events can be lost. Instead, reconcile all the resources on every
execution of reconciliation. On first this might sound a little opinionated, but there is a
sound agreement between the developers that this is the way to go.

Note that this is also consistent with Kubernetes 
[level based](https://cloud.redhat.com/blog/kubernetes-operators-best-practices) reconciliation approach. 

### Can I re-schedule a reconciliation, possibly with a specific delay?

Yes, this can be done
using [`UpdateControl`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/UpdateControl.java)
and [`DeleteControl`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/DeleteControl.java)
, see:

```java 
  @Override
  public UpdateControl<MyCustomResource> reconcile(
     EventSourceTestCustomResource resource, Context context) {
    ...
    return UpdateControl.patchStatus(resource).rescheduleAfter(10, TimeUnit.SECONDS);
  }
```

without an update:

```java 
  @Override
  public UpdateControl<MyCustomResource> reconcile(
     EventSourceTestCustomResource resource, Context context) {
    ...
    return UpdateControl.<MyCustomResource>noUpdate().rescheduleAfter(10, TimeUnit.SECONDS);
  }
```

Although you might consider using `EventSources`, to handle reconciliation triggering in a smarter
way. 

### How can I run an operator without cluster scope rights?

By default, JOSDK requires access to CRs at cluster scope. You may not be granted such
rights and you will see some error at startup that looks like:

```plain
io.fabric8.kubernetes.client.KubernetesClientException: Failure executing: GET at: https://kubernetes.local.svc/apis/mygroup/v1alpha1/mycr. Message: Forbidden! Configured service account doesn't have access. Service account may have been revoked. mycrs.mygroup is forbidden: User "system:serviceaccount:ns:sa" cannot list resource "mycrs" in API group "mygroup" at the cluster scope.
```

To restrict the operator to a set of namespaces, you may override which namespaces are watched by a reconciler
at [Reconciler-level configuration](../configuration.md#reconciler-level-configuration):

```java
Operator operator;
Reconciler reconciler;
...
operator.register(reconciler, configOverrider ->
        configOverrider.settingNamespace("mynamespace"));
```
Note that configuring the watched namespaces can also be done using the `@ControllerConfiguration` annotation.

Furthermore, you may not be able to list CRDs at startup which is required when `checkingCRDAndValidateLocalModel`
is `true` (`false` by default). To disable, set it to `false` at [Operator-level configuration](../configuration#operator-level-configuration):

```java
Operator operator = new Operator( override -> override.checkingCRDAndValidateLocalModel(false));
```

### I'm managing an external resource that has a generated ID, where should I store that?

It is common that a non-Kubernetes or external resource is managed from a controller. Those external resources might
have a generated ID, so are not simply addressable based on the spec of a custom resources. Therefore, the 
generated ID needs to be stored somewhere in order to address the resource during the subsequent reconciliations.

Usually there are two options you can consider to store the ID:

1. Create a separate resource (usually ConfigMap, Secret or dedicated CustomResource) where you store the ID.
2. Store the ID in the status of the custom resource.

Note that both approaches are a bit tricky, since you have to guarantee the resources are cached for the next
reconciliation. For example if you patch the status at the end of the reconciliation (`UpdateControl.patchStatus(...)`)
it is not guaranteed that during the next reconciliation you will see the fresh resource. Therefore, controllers
which do this, usually cache the updated status in memory to make sure it is present for next reconciliation.

Dependent Resources feature supports the [first approach](../dependent-resources/_index.md#external-state-tracking-dependent-resources).
    
### How can I skip the reconciliation of a dependent resource?

Skipping workflow reconciliation altogether is possible with the explicit invocation feature since v5. 
You can read more about this in [v5 release notes](https://javaoperatorsdk.io/blog/2025/01/06/version-5-released/#explicit-workflow-invocation).

However, what if you want to avoid reconciling a single dependent resource based on some state?
First of all, remember that the dependent resource won't be modified if the desired state and the actual state match.
Moreover, it is generally a good practice to reconcile all your resources, JOSDK taking care of only processing the
resources which state doesn't match the desired one.
However, in some corner cases (for example, if it is expensive to compute the desired state or compare it to the actual
state), it is somtimes useful to be able to only skip the reconcilation of some resources but not all, if it is known
that they don't need to be processed based for example on the status of the custom resource.

A common mistake is to use `ReconcilePrecondition`, if the condition does not hold it will delete the resources.
This is by design (although it's true that the name of this condition might be misleading), but not what we want in this
case.

The way to go is to override the matcher in the dependent resource:

```java
public Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    if (alreadyIsCertainState(primary.getStatus())) {
        return true;
    } else {
        return super.match(actual, desired, primary, context);
    }
}
```

This will make sure that the dependent resource is not updated if the primary resource is in certain state.

### How to fix `sun.security.provider.certpath.SunCertPathBuilderException` on Rancher Desktop and k3d/k3s Kubernetes

It's a common issue when using k3d and the fabric8 client tries to connect to the cluster an exception is thrown:

```
Caused by: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
	at java.base/sun.security.ssl.Alert.createSSLException(Alert.java:131)
	at java.base/sun.security.ssl.TransportContext.fatal(TransportContext.java:352)
	at java.base/sun.security.ssl.TransportContext.fatal(TransportContext.java:295)
```

The cause is that fabric8 kubernetes client does not handle elliptical curve encryption by default. To fix this, add
the following dependency on the classpath:

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcpkix-jdk15on</artifactId>
</dependency>
```
