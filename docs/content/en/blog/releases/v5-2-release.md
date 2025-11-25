---
title: Version 5.2 Released!
date: 2025-11-25
---

We're pleased to announce the release of Java Operator SDK v5.2! This minor version brings several powerful new features
and improvements that enhance the framework's capabilities for building Kubernetes operators. This release focuses on
flexibility, external resource management, and advanced reconciliation patterns.

## Key Features

### ResourceIDMapper for External Resources

One of the most significant improvements in 5.2 is the introduction of a unified approach to working with custom ID types
across the framework through [`ResourceIDMapper`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/ResourceIDMapper.java)
and [`ResourceIDProvider`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/ResourceIDProvider.java).

Previously, when working with external resources (non-Kubernetes resources), the framework assumed resource IDs could always
be represented as strings. This limitation made it challenging to work with external systems that use complex ID types.

Now, you can define custom ID types for your external resources by implementing the `ResourceIDProvider` interface:

```java
public class MyExternalResource implements ResourceIDProvider<MyCustomID> {
    @Override
    public MyCustomID getResourceID() {
        return new MyCustomID(this.id);
    }
}
```

This capability is integrated across multiple components:
- `ExternalResourceCachingEventSource`
- `ExternalBulkDependentResource`
- `AbstractExternalDependentResource` and its subclasses

If you cannot modify the external resource class (e.g., it's generated or final), you can provide a custom
`ResourceIDMapper` to the components above.

See the [migration guide](/docs/migration/v5-2-migration) for detailed migration instructions.

### Trigger Reconciliation on All Events

Version 5.2 introduces a new execution mode that provides finer control over when reconciliation occurs. By setting
[`triggerReconcilerOnAllEvent`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ControllerConfiguration.java#L81)
to `true`, your `reconcile` method will be called for **every** event, including `Delete` events.

This is particularly useful when:
- Only some primary resources need finalizers (e.g., some resources create external resources, others don't)
- You maintain custom in-memory caches that need cleanup without using finalizers
- You need fine-grained control over resource lifecycle

When enabled:
- The `reconcile` method receives the last known state even if the resource is deleted
- Check deletion status using `Context.isPrimaryResourceDeleted()`
- Retry, rate limiting, and rescheduling work normally
- You manage finalizers explicitly using `PrimaryUpdateAndCacheUtils`

Example:

```java
@ControllerConfiguration(triggerReconcilerOnAllEvent = true)
public class MyReconciler implements Reconciler<MyResource> {

    @Override
    public UpdateControl<MyResource> reconcile(MyResource resource, Context<MyResource> context) {
        if (context.isPrimaryResourceDeleted()) {
            // Handle deletion
            cleanupCache(resource);
            return UpdateControl.noUpdate();
        }
        // Normal reconciliation
        return UpdateControl.patchStatus(resource);
    }
}
```

See the detailed [documentation](/docs/documentation/reconciler#trigger-reconciliation-for-all-events) and
[integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/triggerallevent/finalizerhandling).

### Expectation Pattern Support (Experimental)

The framework now provides built-in support for the [expectations pattern](https://ahmet.im/blog/controller-pitfalls/#expectations-pattern),
a common Kubernetes controller design pattern that ensures secondary resources are in an expected state before proceeding.

The expectation pattern helps avoid race conditions and ensures your controller makes decisions based on the most current
state of your resources. The implementation is available in the
[`io.javaoperatorsdk.operator.processing.expectation`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/expectation/)
package.

Example usage:

```java
public class MyReconciler implements Reconciler<MyResource> {

    private final ExpectationManager<MyResource> expectationManager = new ExpectationManager<>();

    @Override
    public UpdateControl<MyResource> reconcile(MyResource primary, Context<MyResource> context) {

        // Exit early if expectation is not yet fulfilled or timed out
        if (expectationManager.ongoingExpectationPresent(primary, context)) {
            return UpdateControl.noUpdate();
        }

        var deployment = context.getSecondaryResource(Deployment.class);
        if (deployment.isEmpty()) {
            createDeployment(primary, context);
            expectationManager.setExpectation(
                primary, Duration.ofSeconds(30), deploymentReadyExpectation(context));
            return UpdateControl.noUpdate();
        }

        // Check if expectation is fulfilled
        var result = expectationManager.checkExpectation("deploymentReady", primary, context);
        if (result.isFulfilled()) {
            return updateStatusReady(primary);
        } else if (result.isTimedOut()) {
            return updateStatusTimeout(primary);
        }

        return UpdateControl.noUpdate();
    }
}
```

This feature is marked as `@Experimental` as we gather feedback and may refine the API based on user experience. Future
versions may integrate this pattern directly into Dependent Resources and Workflows.

See the [documentation](/docs/documentation/reconciler#expectations) and
[integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/expectation/ExpectationReconciler.java).

### Field Selectors for InformerEventSource

You can now use field selectors when configuring `InformerEventSource`, allowing you to filter resources at the server
side before they're cached locally. This reduces memory usage and network traffic by only watching resources that match
your criteria.

Field selectors work similarly to label selectors but filter on resource fields like `metadata.name` or `status.phase`:

```java
@Informer(
    fieldSelector = @FieldSelector(
        fields = @Field(key = "status.phase", value = "Running")
    )
)
```

This is particularly useful when:
- You only care about resources in specific states
- You want to reduce the memory footprint of your operator
- You're watching cluster-scoped resources and only need a subset

See the [integration test](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/fieldselector/FieldSelectorIT.java)
for examples.

### AggregatedMetrics for Multiple Metrics Providers

The new `AggregatedMetrics` class implements the composite pattern, allowing you to combine multiple metrics
implementations. This is useful when you need to send metrics to different monitoring systems simultaneously.

```java
// Create individual metrics instances
Metrics micrometerMetrics = MicrometerMetrics.withoutPerResourceMetrics(registry);
Metrics customMetrics = new MyCustomMetrics();
Metrics loggingMetrics = new LoggingMetrics();

// Combine them into a single aggregated instance
Metrics aggregatedMetrics = new AggregatedMetrics(List.of(
    micrometerMetrics,
    customMetrics,
    loggingMetrics
));

// Use with your operator
Operator operator = new Operator(client, o -> o.withMetrics(aggregatedMetrics));
```

This enables hybrid monitoring strategies, such as sending metrics to both Prometheus and a custom logging system.

See the [observability documentation](/docs/documentation/observability#aggregated-metrics) for more details.

## Additional Improvements

### GenericRetry Enhancements

- `GenericRetry` no longer provides a mutable singleton instance, improving thread safety
- Configurable duration for initial retry interval

### Test Infrastructure Improvements

- Ability to override test infrastructure Kubernetes client separately, providing more flexibility in testing scenarios

### Fabric8 Client Update

Updated to Fabric8 Kubernetes Client 7.4.0, bringing the latest features and bug fixes from the client library.

## Experimental Annotations

Starting with this release, new features marked as experimental will be annotated with `@Experimental`. This annotation
indicates that while we intend to support the feature, the API may evolve based on user feedback.

## Migration Notes

For most users, upgrading to 5.2 should be straightforward. The main breaking change involves the introduction of
`ResourceIDMapper` for external resources. If you're using external dependent resources or bulk dependents with custom
ID types, please refer to the [migration guide](/docs/migration/v5-2-migration).

## Getting Started

Update your dependency to version 5.2.0:

```xml
<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework</artifactId>
    <version>5.2.0</version>
</dependency>
```

## All Changes

You can see all changes in the [comparison view](https://github.com/operator-framework/java-operator-sdk/compare/v5.1.0...v5.2.0).

## Feedback

As always, we welcome your feedback! Please report issues or suggest improvements on our
[GitHub repository](https://github.com/operator-framework/java-operator-sdk/issues).

Happy operator building! 🚀
