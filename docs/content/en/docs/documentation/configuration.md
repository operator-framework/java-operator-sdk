---
title: Configurations
weight: 55
---

The Java Operator SDK (JOSDK) provides several abstractions that work great out of the 
box. However, while we strive to cover the most common cases with the default behavior, we also 
recognize that that default behavior is not always what any given user might want for their 
operator. Numerous configuration options are therefore provided to help people tailor the 
framework to their needs.

Configuration options act at several levels, depending on which behavior you wish to act upon:
- `Operator`-level using `ConfigurationService`
- `Reconciler`-level using `ControllerConfiguration`
- `DependentResouce`-level using the `DependentResourceConfigurator` interface
- `EventSource`-level: some event sources, such as `InformerEventSource`, might need to be 
  fine-tuned to properly identify which events will trigger the associated reconciler.

## Operator-level configuration

Configuration that impacts the whole operator is performed via the `ConfigurationService` class. 
`ConfigurationService` is an abstract class, and the implementation can be different based 
on which flavor of the framework is used. For example Quarkus Operator SDK replaces the 
default implementation. Configurations are initialized with sensible defaults, but can 
be changed during initialization.

For instance, if you wish to not validate that the CRDs are present on your cluster when the 
operator starts and configure leader election, you would do something similar to:

```java
Operator operator = new Operator( override -> override
        .checkingCRDAndValidateLocalModel(false)
        .withLeaderElectionConfiguration(new LeaderElectionConfiguration("bar", "barNS")));
```

## Reconciler-level configuration

While reconcilers are typically configured using the `@ControllerConfiguration` annotation, it 
is also possible to override the configuration at runtime, when the reconciler is registered 
with the operator instance, either by passing it a completely new `ControllerConfiguration` 
instance or by preferably overriding some aspects of the current configuration using a 
`ControllerConfigurationOverrider` `Consumer`:

```java
Operator operator;
Reconciler reconciler;
...
operator.register(reconciler, configOverrider ->
        configOverrider.withFinalizer("my-nifty-operator/finalizer").withLabelSelector("foo=bar"));
```

## Dynamically Changing Target Namespaces

A controller can be configured to watch a specific set of namespaces in addition of the
namespace in which it is currently deployed or the whole cluster. The framework supports
dynamically changing the list of these namespaces while the operator is running.
When a reconciler is registered, an instance of
[`RegisteredController`](https://github.com/java-operator-sdk/java-operator-sdk/blob/ec37025a15046d8f409c77616110024bf32c3416/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/RegisteredController.java#L5)
is returned, providing access to the methods allowing users to change watched namespaces as the
operator is running.

A typical scenario would probably involve extracting the list of target namespaces from a
`ConfigMap` or some other input but this part is out of the scope of the framework since this is
use-case specific. For example, reacting to changes to a `ConfigMap` would probably involve
registering an associated `Informer` and then calling the `changeNamespaces` method on
`RegisteredController`.

```java

public static void main(String[] args) {
    KubernetesClient client = new DefaultKubernetesClient();
    Operator operator = new Operator(client);
    RegisteredController registeredController = operator.register(new WebPageReconciler(client));
    operator.installShutdownHook();
    operator.start();

    // call registeredController further while operator is running
}

```

If watched namespaces change for a controller, it might be desirable to propagate these changes to
`InformerEventSources` associated with the controller. In order to express this,
`InformerEventSource` implementations interested in following such changes need to be
configured appropriately so that the `followControllerNamespaceChanges` method returns `true`:

```java

@ControllerConfiguration
public class MyReconciler implements Reconciler<TestCustomResource> {

   @Override
   public Map<String, EventSource> prepareEventSources(
      EventSourceContext<ChangeNamespaceTestCustomResource> context) {

    InformerEventSource<ConfigMap, TestCustomResource> configMapES =
        new InformerEventSource<>(InformerEventSourceConfiguration.from(ConfigMap.class, TestCustomResource.class)
            .withNamespacesInheritedFromController(context)
            .build(), context);

    return EventSourceUtils.nameEventSources(configMapES);
  }

}
```

As seen in the above code snippet, the informer will have the initial namespaces inherited from
controller, but also will adjust the target namespaces if it changes for the controller.

See also
the [integration test](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/changenamespace)
for this feature.

## DependentResource-level configuration

`DependentResource` implementations can implement the `DependentResourceConfigurator` interface 
to pass information to the implementation. For example, the SDK 
provides specific support for the `KubernetesDependentResource`, which can be configured via the 
`@KubernetesDependent` annotation. This annotation is, in turn, converted into a 
`KubernetesDependentResourceConfig` instance, which is then passed to the `configureWith` method 
implementation.

TODO

## EventSource-level configuration

TODO
