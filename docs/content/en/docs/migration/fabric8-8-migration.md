---
title: Migrating from SDK 5 to SDK 6
description: Updating operators for SDK 6, Fabric8 Kubernetes Client 8, and Jackson 3
---

## Breaking changes in SDK 6

Java Operator SDK 6 introduces source and binary incompatibilities through its upgrade to
Fabric8 8 and Jackson 3. Upgrade the SDK dependencies and `operator-framework-bom` together,
migrate custom Jackson integrations, and recompile your operator. This is not a drop-in
replacement for SDK 5.

## Fabric8 Kubernetes Client 8

The SDK now uses Fabric8 Kubernetes Client `8.0.0`, which requires Java 17 or newer, matching the
SDK's existing Java baseline. Align any explicit Fabric8 dependencies and the
`crd-generator-maven-plugin` with this version.

See the [Fabric8 8 migration guide](https://github.com/fabric8io/kubernetes-client/blob/v8.0.0/doc/MIGRATION-v8.md)
for the complete list of client and CRD generator changes.

## Jackson 3

The Kubernetes client, resource matchers, and YAML configuration provider now use Jackson 3.
Code that uses the client's Jackson types must migrate from `com.fasterxml.jackson.core`,
`com.fasterxml.jackson.databind`, and `com.fasterxml.jackson.dataformat` to the corresponding
`tools.jackson` packages. Maven dependencies use `tools.jackson.core:jackson-core`,
`tools.jackson.core:jackson-databind`, and `tools.jackson.dataformat:jackson-dataformat-yaml`.
Jackson annotations such as `@JsonProperty` remain in `com.fasterxml.jackson.annotation`,
provided by `com.fasterxml.jackson.core:jackson-annotations`.

Custom serializers, deserializers, and databind annotations must use Jackson 3 types:
`ValueSerializer`, `ValueDeserializer`, and `tools.jackson.databind.annotation`. Jackson 2
databind annotations are not used by the client, even when Jackson 2 is also on the classpath.
Java time support is built in, so remove `JavaTimeModule` registrations.

Jackson 3 mappers are immutable. Use builders instead of `registerModule` or `configure`.
When supplying a custom mapper to the Kubernetes client, preserve the Fabric8 compatibility
defaults:

```java
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.KubernetesSerialization;
import io.fabric8.kubernetes.model.jackson.Jackson2JdkTypesModule;
import tools.jackson.databind.json.JsonMapper;

var mapper = JsonMapper.builderWithJackson2Defaults()
    .addModule(new Jackson2JdkTypesModule())
    .build();
var client = new KubernetesClientBuilder()
    .withKubernetesSerialization(new KubernetesSerialization(mapper, true))
    .build();
```

The default client already applies these compatibility settings. Supplying a mapper configures
a copy; the original mapper is not modified. See the Fabric8 migration guide for wire-format
differences that cannot be restored, and review regenerated CRDs before applying them.

## HTTP client selection

The `operator-framework` module continues to use the JDK HTTP client by default. Fabric8's
default HTTP client is now `kubernetes-httpclient-vertx-5`, which the core module excludes so
that it does not bring a second HTTP implementation into operators.

If you select Vert.x through `fabric8-httpclient-impl.name`, use `vertx-5` instead of `vertx`.
Explicit Vert.x dependencies should use `io.fabric8:kubernetes-httpclient-vertx-5` and compatible
Vert.x 5 dependencies. Applications depending only on `operator-framework-core` must provide
their chosen Fabric8 HTTP client implementation.

Vert.x 5 requires Netty 4.2. Check application and platform BOMs for dependencies pinned to
Vert.x 4 or Netty 4.1 before selecting this client; incompatible versions can prevent client
startup. See the Fabric8 migration guide if you need to remain on Vert.x 4.

The Jetty HTTP client now uses Jetty 12.1 and cannot share a classpath with Jetty 9, 10, or 11.
Upgrade any application dependencies using older Jetty versions or choose a different
Fabric8 HTTP client implementation.

## CRD generation

Fabric8 removed the `crd-generator-apt` annotation processor. Use the
[`crd-generator-maven-plugin`](../../documentation/features/#automatic-crd-generation) instead.
Only `apiextensions.k8s.io/v1` CRDs are generated.
