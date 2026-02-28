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

## Loading Configuration from External Sources

JOSDK ships a `ConfigLoader` that bridges any key-value configuration source to the operator and
controller configuration APIs. This lets you drive operator behaviour from environment variables,
system properties, YAML files, or any config library (MicroProfile Config, SmallRye Config,
Spring Environment, etc.) without writing glue code by hand.

### Architecture

The system is built around two thin abstractions:

- **[`ConfigProvider`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework/src/main/java/io/javaoperatorsdk/operator/config/loader/ConfigProvider.java)**
  — a single-method interface that resolves a typed value for a dot-separated key:

  ```java
  public interface ConfigProvider {
      <T> Optional<T> getValue(String key, Class<T> type);
  }
  ```

- **[`ConfigLoader`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework/src/main/java/io/javaoperatorsdk/operator/config/loader/ConfigLoader.java)**
  — reads all known JOSDK keys from a `ConfigProvider` and returns
  `Consumer<ConfigurationServiceOverrider>` / `Consumer<ControllerConfigurationOverrider<R>>`
  values that you pass directly to the `Operator` constructor or `operator.register()`.

The default `ConfigLoader` (no-arg constructor) stacks environment variables over system
properties: environment variables win, system properties are the fallback.

```java
// uses env vars + system properties out of the box
Operator operator = new Operator(ConfigLoader.getDefault().applyConfigs());
```

### Built-in Providers

| Provider | Source | Key mapping |
|---|---|---|
| `EnvVarConfigProvider` | `System.getenv()` | dots and hyphens → underscores, upper-cased (`josdk.check-crd` → `JOSDK_CHECK_CRD`) |
| `PropertiesConfigProvider` | `java.util.Properties` or `.properties` file | key used as-is; use `PropertiesConfigProvider.systemProperties()` to read Java system properties |
| `YamlConfigProvider` | YAML file | dot-separated key traverses nested mappings |
| `AgregatePriorityListConfigProvider` | ordered list of providers | first non-empty result wins |

All string-based providers convert values to the target type automatically.
Supported types: `String`, `Boolean`, `Integer`, `Long`, `Double`, `Duration` (ISO-8601, e.g. `PT30S`).

### Plugging in Any Config Library

`ConfigProvider` is a single-method interface, so adapting any config library takes only a few
lines. As an example, here is an adapter for
[SmallRye Config](https://smallrye.io/smallrye-config/):

```java
public class SmallRyeConfigProvider implements ConfigProvider {

    private final SmallRyeConfig config;

    public SmallRyeConfigProvider(SmallRyeConfig config) {
        this.config = config;
    }

    @Override
    public <T> Optional<T> getValue(String key, Class<T> type) {
        return config.getOptionalValue(key, type);
    }
}
```

The same pattern applies to MicroProfile Config, Spring `Environment`, Apache Commons
Configuration, or any other library that can look up typed values by string key.

### Wiring Everything Together

Pass the `ConfigLoader` results when constructing the operator and registering reconcilers:

```java
// Load operator-wide config from a YAML file via SmallRye Config
URL configUrl = MyOperator.class.getResource("/application.yaml");
var configLoader = new ConfigLoader(
    new SmallRyeConfigProvider(
        new SmallRyeConfigBuilder()
            .withSources(new YamlConfigSource(configUrl))
            .build()));

// applyConfigs() → Consumer<ConfigurationServiceOverrider>
Operator operator = new Operator(configLoader.applyConfigs());

// applyControllerConfigs(name) → Consumer<ControllerConfigurationOverrider<R>>
operator.register(new MyReconciler(),
    configLoader.applyControllerConfigs(MyReconciler.NAME));
```

Only keys that are actually present in the source are applied; everything else retains its
programmatic or annotation-based default.

You can also compose multiple sources with explicit priority using
`AgregatePriorityListConfigProvider`:

```java
var configLoader = new ConfigLoader(
    new AgregatePriorityListConfigProvider(List.of(
        new EnvVarConfigProvider(),          // highest priority
        PropertiesConfigProvider.systemProperties(),
        new YamlConfigProvider(Path.of("config/operator.yaml"))  // lowest priority
    )));
```

### Operator-Level Configuration Keys

All operator-level keys are prefixed with `josdk.`.

#### General

| Key | Type | Description |
|---|---|---|
| `josdk.check-crd` | `Boolean` | Validate CRDs against local model on startup |
| `josdk.close-client-on-stop` | `Boolean` | Close the Kubernetes client when the operator stops |
| `josdk.use-ssa-to-patch-primary-resource` | `Boolean` | Use Server-Side Apply to patch the primary resource |
| `josdk.clone-secondary-resources-when-getting-from-cache` | `Boolean` | Clone secondary resources on cache reads |

#### Reconciliation

| Key | Type | Description |
|---|---|---|
| `josdk.reconciliation.concurrent-threads` | `Integer` | Thread pool size for reconciliation |
| `josdk.reconciliation.termination-timeout` | `Duration` | How long to wait for in-flight reconciliations to finish on shutdown |

#### Workflow

| Key | Type | Description |
|---|---|---|
| `josdk.workflow.executor-threads` | `Integer` | Thread pool size for workflow execution |

#### Informer

| Key | Type | Description |
|---|---|---|
| `josdk.informer.cache-sync-timeout` | `Duration` | Timeout for the initial informer cache sync |
| `josdk.informer.stop-on-error-during-startup` | `Boolean` | Stop the operator if an informer fails to start |

#### Dependent Resources

| Key | Type | Description |
|---|---|---|
| `josdk.dependent-resources.ssa-based-create-update-match` | `Boolean` | Use SSA-based matching for dependent resource create/update |

#### Leader Election

Leader election is activated when at least one `josdk.leader-election.*` key is present.
`josdk.leader-election.lease-name` is required when any other leader-election key is set.
Setting `josdk.leader-election.enabled=false` suppresses leader election even if other keys are
present.

| Key | Type | Description |
|---|---|---|
| `josdk.leader-election.enabled` | `Boolean` | Explicitly enable (`true`) or disable (`false`) leader election |
| `josdk.leader-election.lease-name` | `String` | **Required.** Name of the Kubernetes Lease object used for leader election |
| `josdk.leader-election.lease-namespace` | `String` | Namespace for the Lease object (defaults to the operator's namespace) |
| `josdk.leader-election.identity` | `String` | Unique identity for this instance; defaults to the pod name |
| `josdk.leader-election.lease-duration` | `Duration` | How long a lease is valid (default `PT15S`) |
| `josdk.leader-election.renew-deadline` | `Duration` | How long the leader tries to renew before giving up (default `PT10S`) |
| `josdk.leader-election.retry-period` | `Duration` | How often a candidate polls while waiting to become leader (default `PT2S`) |

### Controller-Level Configuration Keys

All controller-level keys are prefixed with `josdk.controller.<controller-name>.`, where
`<controller-name>` is the value returned by the reconciler's name (typically set via
`@ControllerConfiguration(name = "...")`).

#### General

| Key | Type | Description |
|---|---|---|
| `josdk.controller.<name>.finalizer` | `String` | Finalizer string added to managed resources |
| `josdk.controller.<name>.generation-aware` | `Boolean` | Skip reconciliation when the resource generation has not changed |
| `josdk.controller.<name>.label-selector` | `String` | Label selector to filter watched resources |
| `josdk.controller.<name>.max-reconciliation-interval` | `Duration` | Maximum interval between reconciliations even without events |
| `josdk.controller.<name>.field-manager` | `String` | Field manager name used for SSA operations |
| `josdk.controller.<name>.trigger-reconciler-on-all-events` | `Boolean` | Trigger reconciliation on every event, not only meaningful changes |

#### Informer

| Key | Type | Description |
|---|---|---|
| `josdk.controller.<name>.informer.label-selector` | `String` | Label selector for the primary resource informer (alias for `label-selector`) |
| `josdk.controller.<name>.informer.list-limit` | `Long` | Page size for paginated informer list requests; omit for no pagination |

#### Retry

If any `retry.*` key is present, a `GenericRetry` is configured starting from the
[default limited exponential retry](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/retry/GenericRetry.java).
Only explicitly set keys override the defaults.

| Key | Type | Description |
|---|---|---|
| `josdk.controller.<name>.retry.max-attempts` | `Integer` | Maximum number of retry attempts |
| `josdk.controller.<name>.retry.initial-interval` | `Long` (ms) | Initial backoff interval in milliseconds |
| `josdk.controller.<name>.retry.interval-multiplier` | `Double` | Exponential backoff multiplier |
| `josdk.controller.<name>.retry.max-interval` | `Long` (ms) | Maximum backoff interval in milliseconds |

#### Rate Limiter

The rate limiter is only activated when `rate-limiter.limit-for-period` is present and has a
positive value. `rate-limiter.refresh-period` is optional and falls back to the default of 10 s.

| Key | Type | Description |
|---|---|---|
| `josdk.controller.<name>.rate-limiter.limit-for-period` | `Integer` | Maximum number of reconciliations allowed per refresh period. Must be positive to activate the limiter |
| `josdk.controller.<name>.rate-limiter.refresh-period` | `Duration` | Window over which the limit is counted (default `PT10S`) |

