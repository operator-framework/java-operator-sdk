---
title: Configuring JOSDK
description: Configuration options 
layout: docs
permalink: /docs/configuration
---

# Configuration options

The Java Operator SDK (JOSDK for short) provides several abstractions that work great out of the 
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
An instance is provided by the `ConfigurationServiceProvider.instance()` method. This is the 
normal way for user-code to retrieve the current `ConfigurationService` instance. Sensible 
defaults are provided but you can change the default behavior by overriding the current 
configuration using `ConfigurationServiceProvider.overrideCurrent` method, providing a 
`ConfigurationServiceOverrider` `Consumer` that will apply the modifications you wish to perform 
on the configuration.

For instance, if you wish to not validate that the CRDs are present on your cluster when the 
operator starts and configure leader election, you would do something similar to:

```java
ConfigurationServiceProvider.overrideCurrent(o -> o.checkingCRDAndValidateLocalModel(false)
        .withLeaderElectionConfiguration(new LeaderElectionConfiguration("bar", "barNS")));
```

Note that you can also obtain the same result by passing the `ConfigurationServiceOverrider` 
`Consumer` instance to the `Operator` constructor:

```java
new Operator(o -> o.checkingCRDAndValidateLocalModel(false)
        .withLeaderElectionConfiguration(new LeaderElectionConfiguration("bar","barNS")));
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
