---
title: Configurations
weight: 56
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

## DependentResource-level configuration

`DependentResource` implementations can implement the `DependentResourceConfigurator` interface 
to pass information to the implementation. For example, the SDK 
provides specific support for the `KubernetesDependentResource`, which can be configured via the 
`@KubernetesDependent` annotation. This annotation is, in turn, converted into a 
`KubernetesDependentResourceConfig` instance, which is then passed to the `configureWith` method 
implementation.

TODO: still subject to change / uniformization

## EventSource-level configuration

TODO
