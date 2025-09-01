---
title: FAQ
weight: 90
---

## Events and Reconciliation

### How can I access the events that triggered reconciliation?

In v1.* versions, events were exposed to `Reconciler` (then called `ResourceController`). This included custom resource events (Create, Update) and events from Event Sources. After extensive discussions with golang controller-runtime developers, we decided to remove event access.

**Why this change was made:**
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
  // ... reconciliation logic
  return UpdateControl.patchStatus(resource).rescheduleAfter(10, TimeUnit.SECONDS);
}
```

**Without an update:**
```java 
@Override
public UpdateControl<MyCustomResource> reconcile(
   EventSourceTestCustomResource resource, Context context) {
  // ... reconciliation logic
  return UpdateControl.<MyCustomResource>noUpdate().rescheduleAfter(10, TimeUnit.SECONDS);
}
```

**Note**: Consider using `EventSources` for smarter reconciliation triggering instead of time-based scheduling.

### How can I make status updates trigger reconciliation?

By default, the framework filters out events that don't increase the `generation` field of the primary resource's metadata. Since `generation` typically only increases when the `.spec` field changes, status-only changes won't trigger reconciliation.

To change this behavior, set [`generationAwareEventProcessing`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java#L43) to `false`:

```java
@ControllerConfiguration(generationAwareEventProcessing = false)
static class TestCustomReconciler implements Reconciler<TestCustomResource> {
    @Override
    public UpdateControl<TestCustomResource> reconcile(
        TestCustomResource resource, Context<TestCustomResource> context) {
        // reconciliation logic
    }
}
```

For secondary resources, every change should trigger reconciliation by default, except when you add explicit filters or use dependent resource implementations that filter out self-triggered changes. See [related docs](../documentation/dependent-resource-and-workflows/dependent-resources.md#caching-and-event-handling-in-kubernetesdependentresource).

## Permissions and Access Control

### How can I run an operator without cluster-scope rights?

By default, JOSDK requires cluster-scope access to custom resources. Without these rights, you'll see startup errors like:

```plain
io.fabric8.kubernetes.client.KubernetesClientException: Failure executing: GET at: https://kubernetes.local.svc/apis/mygroup/v1alpha1/mycr. Message: Forbidden! Configured service account doesn't have access. Service account may have been revoked. mycrs.mygroup is forbidden: User "system:serviceaccount:ns:sa" cannot list resource "mycrs" in API group "mygroup" at the cluster scope.
```

**Solution 1: Restrict to specific namespaces**

Override watched namespaces using [Reconciler-level configuration](../configuration.md#reconciler-level-configuration):

```java
Operator operator;
Reconciler reconciler;
// ...
operator.register(reconciler, configOverrider ->
        configOverrider.settingNamespace("mynamespace"));
```

**Note**: You can also configure watched namespaces using the `@ControllerConfiguration` annotation.

**Solution 2: Disable CRD validation**

If you can't list CRDs at startup (required when `checkingCRDAndValidateLocalModel` is `true`), disable it using [Operator-level configuration](../configuration#operator-level-configuration):

```java
Operator operator = new Operator(override -> override.checkingCRDAndValidateLocalModel(false));
```

## State Management

### Where should I store generated IDs for external resources?

When managing external (non-Kubernetes) resources, they often have generated IDs that aren't simply addressable based on your custom resource spec. You need to store these IDs for subsequent reconciliations.

**Storage Options:**
1. **Separate resource** (usually ConfigMap, Secret, or dedicated CustomResource)
2. **Custom resource status field**

**Important considerations:**

Both approaches require guaranteeing resources are cached for the next reconciliation. If you patch status at the end of reconciliation (`UpdateControl.patchStatus(...)`), the fresh resource isn't guaranteed to be available during the next reconciliation. Controllers typically cache updated status in memory to ensure availability.

**Modern solution**: From version 5.1, use [this utility](../documentation/reconciler.md#making-sure-the-primary-resource-is-up-to-date-for-the-next-reconciliation) to ensure updated status is available for the next reconciliation.

**Dependent Resources**: This feature supports [the first approach](../documentation/dependent-resource-and-workflows/dependent-resources.md#external-state-tracking-dependent-resources) natively.

## Advanced Use Cases

### How can I skip the reconciliation of a dependent resource?

Skipping workflow reconciliation altogether is possible with the explicit invocation feature since v5. You can read more about this in [v5 release notes](https://javaoperatorsdk.io/blog/2025/01/06/version-5-released/#explicit-workflow-invocation).

However, what if you want to avoid reconciling a single dependent resource based on some state? First, remember that the dependent resource won't be modified if the desired state and actual state match. Moreover, it's generally good practice to reconcile all your resources, with JOSDK taking care of only processing resources whose state doesn't match the desired one.

However, in some corner cases (for example, if it's expensive to compute the desired state or compare it to the actual state), it's sometimes useful to skip the reconciliation of some resources but not all, if it's known that they don't need processing based on the status of the custom resource.

A common mistake is to use `ReconcilePrecondition`. If the condition doesn't hold, it will delete the resources. This is by design (although the name might be misleading), but not what we want in this case.

The correct approach is to override the matcher in the dependent resource:

```java
public Result<R> match(R actualResource, R desired, P primary, Context<P> context) {
    if (alreadyIsCertainState(primary.getStatus())) {
        return true;
    } else {
        return super.match(actual, desired, primary, context);
    }
}
```

This ensures the dependent resource isn't updated if the primary resource is in a certain state.

## Troubleshooting

### How to fix SSL certificate issues with Rancher Desktop and k3d/k3s

This is a common issue when using k3d and the fabric8 client tries to connect to the cluster:

```
Caused by: javax.net.ssl.SSLHandshakeException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
	at java.base/sun.security.ssl.Alert.createSSLException(Alert.java:131)
	at java.base/sun.security.ssl.TransportContext.fatal(TransportContext.java:352)
	at java.base/sun.security.ssl.TransportContext.fatal(TransportContext.java:295)
```

**Cause**: The fabric8 kubernetes client doesn't handle elliptical curve encryption by default.

**Solution**: Add the following dependency to your classpath:

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcpkix-jdk15on</artifactId>
</dependency>
```
