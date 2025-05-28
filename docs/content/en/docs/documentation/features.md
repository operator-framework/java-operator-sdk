---
title: Other Features
weight: 57
---

The Java Operator SDK (JOSDK) is a high level framework and related tooling aimed at
facilitating the implementation of Kubernetes operators. The features are by default following
the best practices in an opinionated way. However, feature flags and other configuration options
are provided to fine tune or turn off these features.

## Support for Well Known (non-custom) Kubernetes Resources

A Controller can be registered for a non-custom resource, so well known Kubernetes resources like (
`Ingress`, `Deployment`,...).

See
the [integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/deployment)
for reconciling deployments.

```java 
public class DeploymentReconciler
    implements Reconciler<Deployment>, TestExecutionInfoProvider {

    @Override
    public UpdateControl<Deployment> reconcile(
            Deployment resource, Context context) {
        // omitted code
    }
}
```

## Leader Election

Operators are generally deployed with a single running or active instance. However, it is
possible to deploy multiple instances in such a way that only one, called the "leader", processes the
events. This is achieved via a mechanism called "leader election". While all the instances are
running, and even start their event sources to populate the caches, only the leader will process
the events. This means that should the leader change for any reason, for example because it
crashed, the other instances are already warmed up and ready to pick up where the previous
leader left off should one of them become elected leader.

See sample configuration in
the [E2E test](https://github.com/java-operator-sdk/java-operator-sdk/blob/8865302ac0346ee31f2d7b348997ec2913d5922b/sample-operators/leader-election/src/main/java/io/javaoperatorsdk/operator/sample/LeaderElectionTestOperator.java#L21-L23)
.

## Automatic Generation of CRDs

Note that this feature is provided by the
[Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client), not JOSDK itself.

To automatically generate CRD manifests from your annotated Custom Resource classes, you only need
to add the following dependencies to your project:

```xml

<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>crd-generator-apt</artifactId>
    <scope>provided</scope>
</dependency>
```

The CRD will be generated in `target/classes/META-INF/fabric8` (or
in `target/test-classes/META-INF/fabric8`, if you use the `test` scope) with the CRD name
suffixed by the generated spec version. For example, a CR using the `java-operator-sdk.io` group
with a `mycrs` plural form will result in 2 files:

- `mycrs.java-operator-sdk.io-v1.yml`
- `mycrs.java-operator-sdk.io-v1beta1.yml`

**NOTE:**
> Quarkus users using the `quarkus-operator-sdk` extension do not need to add any extra dependency
> to get their CRD generated as this is handled by the extension itself.
