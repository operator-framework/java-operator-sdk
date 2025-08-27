---
title: Configurations
weight: 55
---

The Java Operator SDK (JOSDK) provides abstractions that work great out of the box. However, we recognize that default behavior isn't always suitable for every use case. Numerous configuration options help you tailor the framework to your specific needs.

Configuration options operate at several levels:
- **Operator-level** using `ConfigurationService`
- **Reconciler-level** using `ControllerConfiguration`
- **DependentResource-level** using the `DependentResourceConfigurator` interface
- **EventSource-level** where some event sources (like `InformerEventSource`) need fine-tuning to identify which events trigger the associated reconciler

## Operator-Level Configuration

Configuration that impacts the entire operator is performed via the `ConfigurationService` class. `ConfigurationService` is an abstract class with different implementations based on which framework flavor you use (e.g., Quarkus Operator SDK replaces the default implementation). Configurations initialize with sensible defaults but can be changed during initialization.

For example, to disable CRD validation on startup and configure leader election:

```java
Operator operator = new Operator( override -> override
        .checkingCRDAndValidateLocalModel(false)
        .withLeaderElectionConfiguration(new LeaderElectionConfiguration("bar", "barNS")));
```

## Reconciler-Level Configuration

While reconcilers are typically configured using the `@ControllerConfiguration` annotation, you can also override configuration at runtime when registering the reconciler with the operator. You can either:
- Pass a completely new `ControllerConfiguration` instance
- Override specific aspects using a `ControllerConfigurationOverrider` `Consumer` (preferred)

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

It is possible to define custom annotations to configure custom `DependentResource` implementations. In order to provide
such a configuration mechanism for your own `DependentResource` implementations, they must be annotated with the
`@Configured` annotation. This annotation defines 3 fields that tie everything together:

- `by`, which specifies which annotation class will be used to configure your dependents,
- `with`, which specifies the class holding the configuration object for your dependents and
- `converter`, which specifies the `ConfigurationConverter` implementation in charge of converting the annotation
  specified by the `by` field into objects of the class specified by the `with` field.

`ConfigurationConverter` instances implement a single `configFrom` method, which will receive, as expected, the
annotation instance annotating the dependent resource instance to be configured, but it can also extract information
from the `DependentResourceSpec` instance associated with the `DependentResource` class so that metadata from it can be
used in the configuration, as well as the parent `ControllerConfiguration`, if needed. The role of
`ConfigurationConverter` implementations is to extract the annotation information, augment it with metadata from the
`DependentResourceSpec` and the configuration from the parent controller on which the dependent is defined, to finally
create the configuration object that the `DependentResource` instances will use.

However, one last element is required to finish the configuration process: the target `DependentResource` class must
implement the `ConfiguredDependentResource` interface, parameterized with the annotation class defined by the
`@Configured` annotation `by` field. This interface is called by the framework to inject the configuration at the
appropriate time and retrieve the configuration, if it's available.

For example, `KubernetesDependentResource`, a core implementation that the framework provides, can be configured via the
`@KubernetesDependent` annotation. This set up is configured as follows:

```java

@Configured(
        by = KubernetesDependent.class,
        with = KubernetesDependentResourceConfig.class,
        converter = KubernetesDependentConverter.class)
public abstract class KubernetesDependentResource<R extends HasMetadata, P extends HasMetadata>
        extends AbstractEventSourceHolderDependentResource<R, P, InformerEventSource<R, P>>
        implements ConfiguredDependentResource<KubernetesDependentResourceConfig<R>> {
  // code omitted
}
```

The `@Configured` annotation specifies that `KubernetesDependentResource` instances can be configured by using the
`@KubernetesDependent` annotation, which gets converted into a `KubernetesDependentResourceConfig` object by a
`KubernetesDependentConverter`. That configuration object is then injected by the framework in the
`KubernetesDependentResource` instance, after it's been created, because the class implements the
`ConfiguredDependentResource` interface, properly parameterized.

For more information on how to use this feature, we recommend looking at how this mechanism is implemented for
`KubernetesDependentResource` in the core framework, `SchemaDependentResource` in the samples or `CustomAnnotationDep`
in the `BaseConfigurationServiceTest` test class.

## EventSource-level configuration

TODO
