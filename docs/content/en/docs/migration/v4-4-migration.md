---
title: Migrating from v4.3 to v4.4
layout: docs
permalink: /docs/v4-4-migration
---

## API changes

### ConfigurationService

We have simplified how to deal with the Kubernetes client. Previous versions provided direct
access to underlying aspects of the client's configuration or serialization mechanism. However,
the link between these aspects wasn't as explicit as it should have been. Moreover, the Fabric8
client framework has also revised their serialization architecture in the 6.7 version (see [this
fabric8 pull request](https://github.com/fabric8io/kubernetes-client/pull/4662) for a discussion of
that change), moving from statically configured serialization to a per-client configuration
(though it's still possible to share serialization mechanism between client instances). As a
consequence, we made the following changes to the `ConfigurationService` API:

- Replaced `getClientConfiguration` and `getObjectMapper` methods by a new `getKubernetesClient`
  method: instead of providing the configuration and mapper, you now provide a client instance
  configured according to your needs and the SDK will extract the needed information from it

If you had previously configured a custom configuration or `ObjectMapper`, it is now recommended 
that you do so when creating your client instance, as follows, usually using 
`ConfigurationServiceOverrider.withKubernetesClient`:

```java

class Example {

  public static void main(String[] args) {
    Config config; // your configuration
    ObjectMapper mapper; // your mapper
    final var operator = new Operator(overrider -> overrider.withKubernetesClient(
            new KubernetesClientBuilder()
                    .withConfig(config)
                    .withKubernetesSerialization(new KubernetesSerialization(mapper, true))
                    .build()
        ));
  }
}
```

Consequently, it is now recommended to get the client instance from the `ConfigurationService`.

### Operator

It is now recommended to configure your Operator instance by using a 
`ConfigurationServiceOverrider` when creating it. This allows you to change the default 
configuration values as needed. In particular, instead of passing a Kubernetes client instance 
explicitly to the Operator constructor, it is now recommended to provide that value using 
`ConfigurationServiceOverrider.withKubernetesClient` as shown above.

## Using Server-Side Apply in Dependent Resources

From this version by
default [Dependent Resources](https://javaoperatorsdk.io/docs/documentation/dependent-resource-and-workflows/dependent-resources/) use
[Server Side Apply (SSA)](https://kubernetes.io/docs/reference/using-api/server-side-apply/) to
create and
update Kubernetes resources. A
new [default matching](https://github.com/java-operator-sdk/java-operator-sdk/blob/2cc3bb7710adb8fca14767fbff8d93533dd05ef0/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/KubernetesDependentResource.java#L157-L157)
algorithm is provided for `KubernetesDependentResource` that is based on `managedFields` of SSA. For
details
see [SSABasedGenericKubernetesResourceMatcher](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/SSABasedGenericKubernetesResourceMatcher.java)

Since those features are hard to completely test, we provided feature flags to revert to the
legacy behavior if needed,
see 
in [ConfigurationService](https://github.com/java-operator-sdk/java-operator-sdk/blob/2cc3bb7710adb8fca14767fbff8d93533dd05ef0/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L332-L347)

Note that it is possible to override the related methods/behavior on class level when extending
the `KubernetesDependentResource`. 

The SSA based create/update can be combined with the legacy matcher, simply override the `match` method 
and use the [GenericKubernetesResourceMatcher](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/dependent/kubernetes/GenericKubernetesResourceMatcher.java#L19-L19)
directly. See related [sample](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/sample/ssalegacymatcher/ServiceDependentResource.java#L39-L44).

### Migration from plain Update/Create to SSA Based Patch

Migration to SSA might not be trivial based on the uses cases and the type of managed resources. 
In general this is not a solved problem is Kubernetes. The Java Operator SDK Team tries to follow
the related issues, but in terms of implementation this is not something that the framework explicitly
supports. Thus, no code is added that tries to mitigate related issues. Users should thoroughly
test the migration, and even consider not to migrate in some cases (see feature flags above).

See some related issues in [kubernetes](https://github.com/kubernetes/kubernetes/issues/118725) or
[here](https://github.com/keycloak/keycloak/pull). Please create related issue in JOSDK if any.


