---
title: FAQ
weight: 90
---

### How can I access the events that triggered reconciliation?

In v1.* versions, events were exposed to `Reconciler` (then called `ResourceController`). This included custom resource events (Create, Update) and events from Event Sources. After extensive discussions with golang controller-runtime developers, we decided to remove event access.

**Why this change?**
- Events can be lost in distributed systems
- Best practice is to reconcile all resources on every execution
- Aligns with Kubernetes [level-based](https://cloud.redhat.com/blog/kubernetes-operators-best-practices) reconciliation approach

**Recommendation**: Always reconcile all resources instead of relying on specific events. 

### Can I reschedule a reconciliation with a specific delay?

Yes, you can reschedule reconciliation using [`UpdateControl`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/UpdateControl.java) and [`DeleteControl`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/DeleteControl.java).

**With status update:**

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

**Note**: Consider using `EventSources` for smarter reconciliation triggering instead of time-based scheduling. 

### How can I run an operator without cluster-scope rights?

By default, JOSDK requires cluster-scope access to custom resources. If you don't have these rights, you'll see startup errors like:

```plain
io.fabric8.kubernetes.client.KubernetesClientException: Failure executing: GET at: https://kubernetes.local.svc/apis/mygroup/v1alpha1/mycr. Message: Forbidden! Configured service account doesn't have access. Service account may have been revoked. mycrs.mygroup is forbidden: User "system:serviceaccount:ns:sa" cannot list resource "mycrs" in API group "mygroup" at the cluster scope.
```

**Solution 1: Restrict to specific namespaces**

Override watched namespaces using [Reconciler-level configuration](../configuration.md#reconciler-level-configuration):

```java
Operator operator;
Reconciler reconciler;
...
operator.register(reconciler, configOverrider ->
        configOverrider.settingNamespace("mynamespace"));
```

**Note**: You can also configure watched namespaces using the `@ControllerConfiguration` annotation.

**Solution 2: Disable CRD validation**

If you can't list CRDs at startup (required when `checkingCRDAndValidateLocalModel` is `true`), disable it using [Operator-level configuration](../configuration#operator-level-configuration):

```java
Operator operator = new Operator( override -> override.checkingCRDAndValidateLocalModel(false));
```

### Where should I store generated IDs for external resources?

When managing external (non-Kubernetes) resources, they often have generated IDs that aren't simply addressable based on your custom resource spec. You need to store these IDs for subsequent reconciliations.

**Storage Options:**

1. **Separate resource** (usually ConfigMap, Secret, or dedicated CustomResource)
2. **Custom resource status field**

**Important considerations:**

Both approaches require guaranteeing resources are cached for the next reconciliation. If you patch status at the end of reconciliation (`UpdateControl.patchStatus(...)`), it's not guaranteed the fresh resource will be available during the next reconciliation. Controllers typically cache updated status in memory to ensure availability.

**Modern solution**: From version 5.1, use [this utility](../documentation/reconciler.md#making-sure-the-primary-resource-is-up-to-date-for-the-next-reconciliation) to ensure updated status is available for the next reconciliation.

**Dependent Resources**: The feature supports [the first approach](../documentation/dependent-resource-and-workflows/dependent-resources.md#external-state-tracking-dependent-resources) natively.
    
### How can I make the status update of my custom resource trigger a reconciliation?

The framework checks, by default, when an event occurs, that could trigger a reconciliation, if the event increased the
`generation` field of the primary resource's metadata and filters out the event if it did not. `generation` is typically
only increased when the `.spec` field of a resource is changed. As a result, a change in the `.status` field would not
normally trigger a reconciliation.

To change this behavior, you can set the [
`generationAwareEventProcessing`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java#L43)
to `false`:

```java

@ControllerConfiguration(generationAwareEventProcessing = false)
static class TestCustomReconciler implements Reconciler<TestCustomResource> {

    @Override
    public UpdateControl<TestCustomResource> reconcile(TestCustomResource resource, Context<TestCustomResource> context) {
        // code omitted
    }
}
```

For secondary resources, every change should trigger a reconciliation by default, except when you add explicit filters
or use dependent resource implementations that filter out changes they trigger themselves by default,
see [related docs](../documentation/dependent-resource-and-workflows/dependent-resources.md#caching-and-event-handling-in-kubernetesdependentresource).

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
