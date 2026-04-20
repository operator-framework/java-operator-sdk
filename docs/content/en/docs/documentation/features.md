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
    implements Reconciler<Deployment> {

    @Override
    public UpdateControl<Deployment> reconcile(
            Deployment resource, Context context) {
        // omitted code
    }
}
```

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
