---
title: FAQ
description: Frequently asked questions
layout: docs
permalink: /docs/faq
---

### Q: How can I access the events which triggered the Reconciliation?

In the v1.* version events were exposed to `Reconciler` (which was called `ResourceController`
then). This included events (Create, Update) of the custom resource, but also events produced by
Event Sources. After long discussions also with developers of golang version (controller-runtime),
we decided to remove access to these events. We already advocated to not use events in the
reconciliation logic, since events can be lost. Instead, reconcile all the resources on every
execution of reconciliation. On first this might sound a little opinionated, but there is a
sound agreement between the developers that this is the way to go.

Note that this is also consistent with Kubernetes 
[level based](https://cloud.redhat.com/blog/kubernetes-operators-best-practices) reconciliation approach. 

### Q: Can I re-schedule a reconciliation, possibly with a specific delay?

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

### Q: How can I run an operator without cluster scope rights?

By default, JOSDK requires access to CRs at cluster scope. You may not be granted such
rights and you will see some error at startup that looks like:

```plain
io.fabric8.kubernetes.client.KubernetesClientException: Failure executing: GET at: https://kubernetes.local.svc/apis/mygroup/v1alpha1/mycr. Message: Forbidden! Configured service account doesn't have access. Service account may have been revoked. mycrs.mygroup is forbidden: User "system:serviceaccount:ns:sa" cannot list resource "mycrs" in API group "mygroup" at the cluster scope.
```

To restrict the operator to a set of namespaces, you may override which namespaces are watched by a reconciler
at [Reconciler-level configuration](./configuration.md#reconciler-level-configuration):

```java
Operator operator;
Reconciler reconciler;
...
operator.register(reconciler, configOverrider ->
        configOverrider.settingNamespace("mynamespace"));
```
Note that configuring the watched namespaces can also be done using the `@ControllerConfiguration` annotation.

Furthermore, you may not be able to list CRDs at startup which is required when `checkingCRDAndValidateLocalModel`
is `true` (`false` by default). To disable, set it to `false` at [Operator-level configuration](./configuration.md#operator-level-configuration):

```java
Operator operator = new Operator( override -> override.checkingCRDAndValidateLocalModel(false));
```

### Q: How to fix `sun.security.provider.certpath.SunCertPathBuilderException` on Rancher Desktop and k3d/k3s Kubernetes

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