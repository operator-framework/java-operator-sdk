---
title: Other Features
weight: 57
---

The Java Operator SDK (JOSDK) is a high-level framework and tooling suite for implementing Kubernetes operators. By default, features follow best practices in an opinionated way. However, configuration options and feature flags are available to fine-tune or disable these features.

## Support for Well-Known Kubernetes Resources

Controllers can be registered for standard Kubernetes resources (not just custom resources), such as `Ingress`, `Deployment`, and others.

See the [integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/deployment) for an example of reconciling deployments.

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

Operators are typically deployed with a single active instance. However, you can deploy multiple instances where only one (the "leader") processes events. This is achieved through "leader election."

While all instances run and start their event sources to populate caches, only the leader processes events. If the leader crashes, other instances are already warmed up and ready to take over when a new leader is elected.

See sample configuration in the [E2E test](https://github.com/java-operator-sdk/java-operator-sdk/blob/8865302ac0346ee31f2d7b348997ec2913d5922b/sample-operators/leader-election/src/main/java/io/javaoperatorsdk/operator/sample/LeaderElectionTestOperator.java#L21-L23).

## Automatic CRD Generation

**Note:** This feature is provided by the [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client), not JOSDK itself.

To automatically generate CRD manifests from your annotated Custom Resource classes, add this dependency to your project:

```xml

<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>crd-generator-apt</artifactId>
    <scope>provided</scope>
</dependency>
```

The CRD will be generated in `target/classes/META-INF/fabric8` (or `target/test-classes/META-INF/fabric8` for test scope) with the CRD name suffixed by the generated spec version. 

For example, a CR using the `java-operator-sdk.io` group with a `mycrs` plural form will result in these files:
- `mycrs.java-operator-sdk.io-v1.yml`
- `mycrs.java-operator-sdk.io-v1beta1.yml`

**Note for Quarkus users:** If you're using the `quarkus-operator-sdk` extension, you don't need to add any extra dependency for CRD generation - the extension handles this automatically.
