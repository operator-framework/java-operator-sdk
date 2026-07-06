---
title: Event filters
weight: 50
---

Every [event source](eventing.md) that watches Kubernetes resources is backed by an informer that
receives *add*, *update* and *delete* events from the Kubernetes API. Not every one of those events
is worth a reconciliation: a resource might be touched by another controller, its `.metadata` might
change without any meaningful change to its `.spec`, or your own controller might have caused the
change in the first place. **Event filters** let you decide, per event, whether it should be
propagated and (potentially) trigger a reconciliation. Filtering events as early as possible keeps
your operator efficient by avoiding needless reconciliations.

Filters apply both to the **primary resource** (the resource your `Reconciler` manages) and to any
**secondary resources** watched through an [`InformerEventSource`](eventing.md#informereventsource).

## Filter types

JOSDK defines four functional interfaces in the
[`io.javaoperatorsdk.operator.processing.event.source.filter`](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/filter)
package. Each returns `true` when the event should be **accepted** (propagated) and `false` when it
should be **dropped**:

| Filter | Applies to | Signature |
| --- | --- | --- |
| [`OnAddFilter`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/filter/OnAddFilter.java) | resource add events | `boolean accept(R resource)` |
| [`OnUpdateFilter`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/filter/OnUpdateFilter.java) | resource update events | `boolean accept(R newResource, R oldResource)` |
| [`OnDeleteFilter`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/filter/OnDeleteFilter.java) | resource delete events | `boolean accept(R resource, Boolean deletedFinalStateUnknown)` |
| [`GenericFilter`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/filter/GenericFilter.java) | add, update **and** delete events | `boolean accept(R resource)` |

`GenericFilter` is applied to every kind of event, so it is a convenient way to express a condition
that is independent of the event type. When both a specific filter (e.g. `OnUpdateFilter`) and the
`GenericFilter` are configured, **both** must accept the event for it to be propagated.

All four interfaces are `@FunctionalInterface`s and provide `and(...)`, `or(...)` and `not()`
default methods, so you can compose several conditions:

```java
OnUpdateFilter<MyCustomResource> filter =
    onUpdateFilterA.and(onUpdateFilterB).not();
```

## Configuring filters on the primary resource

Filters for the primary resource are configured through the `@Informer` annotation nested in
`@ControllerConfiguration`. You reference the filter *class* (which must have an accessible no-arg
constructor):

```java
@ControllerConfiguration(informer = @Informer(onUpdateFilter = UpdateFilter.class))
public class MyReconciler implements Reconciler<MyCustomResource> {
  // ...
}
```

```java
public class UpdateFilter implements OnUpdateFilter<MyCustomResource> {
  @Override
  public boolean accept(MyCustomResource newResource, MyCustomResource oldResource) {
    // reconcile only if the value actually changed
    return !newResource.getSpec().getValue().equals(SKIP_VALUE);
  }
}
```

The `@Informer` annotation exposes `onAddFilter`, `onUpdateFilter`, `onDeleteFilter` and
`genericFilter` attributes for the primary resource informer.

## Configuring filters on secondary resources

For secondary resources watched via an `InformerEventSource`, filters are set through the
`InformerEventSourceConfiguration` builder, where you can pass filter instances directly (typically
as lambdas):

```java
@Override
public List<EventSource<?, MyCustomResource>> prepareEventSources(
    EventSourceContext<MyCustomResource> context) {

  var informerConfiguration =
      InformerEventSourceConfiguration.from(ConfigMap.class, MyCustomResource.class)
          .withOnUpdateFilter(
              (newCM, oldCM) -> !newCM.getData().get(VALUE_KEY).equals(SKIP_VALUE))
          .withOnAddFilter(cm -> true)
          .build();

  return List.of(new InformerEventSource<>(informerConfiguration, context));
}
```

The builder provides `withOnAddFilter`, `withOnUpdateFilter`, `withOnDeleteFilter` and
`withGenericFilter`.

{{% alert title="Selectors vs. filters" color="primary" %}}
Filters run **client-side**, after the event has already been received from the API server. If you
can express your condition as a label or field selector, prefer the `labelSelector` /
`fieldSelector` attributes of `@Informer` (or the equivalent builder methods) instead: those are
evaluated **server-side**, so filtered-out resources are never sent to the informer at all and are
not even held in the cache. Use event filters for conditions that cannot be expressed as selectors,
such as comparing the new and old versions of a resource.
{{% /alert %}}

## Default filters

For the primary resource, JOSDK always applies a set of **internal update filters** on top of your
own `onUpdateFilter`. These are what make sure your `Reconciler` is not triggered for updates that
don't require action. An update event is accepted by the default filters if **any** of the following
is true (see
[`InternalEventFilters`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/controller/InternalEventFilters.java)):

- **Generation aware:** the resource's `.metadata.generation` increased. Kubernetes bumps the
  generation whenever the `.spec` changes, so this drops events caused purely by `.metadata` or
  `.status` changes. This part is only active when
  [generation-aware event processing](eventing.md#generation-awareness-and-event-filtering) is
  enabled (the default for resources that support it). For resources without a generation (e.g.
  `Pod`), every update is accepted.
- **Finalizer needed:** the finalizer JOSDK manages was just added or removed. This ensures the
  reconciliation that adds the finalizer is not filtered out.
- **Marked for deletion:** the resource just transitioned to being marked for deletion (a deletion
  timestamp was set), so cleanup logic can run.

### How default filters combine with your filter

The default (internal) update filter is combined with your `onUpdateFilter` using a logical **AND**:
an update event must be accepted by **both** your filter and the internal filter to be propagated.
In other words, your filter can only make the operator *more* selective; it cannot force a
reconciliation for an event that the default filters would otherwise drop.

Note that default filters only concern **update** events on the **primary** resource. Add events are
always processed regardless of the internal filters, delete events are only subject to your
`genericFilter` (if any), and secondary resources have no internal default filters at all.

### Disabling the default filters

If the AND-composition described above is too restrictive — for example, you need full control over
which updates trigger a reconciliation — you can turn the internal update filters off with the
`defaultFilters` attribute:

```java
@ControllerConfiguration(
    defaultFilters = false,
    informer = @Informer(onUpdateFilter = MyUpdateFilter.class))
public class MyReconciler implements Reconciler<MyCustomResource> {
  // ...
}
```

With `defaultFilters = false`:

- Your `onUpdateFilter` becomes the **sole** update filter and has full control over which update
  events are propagated.
- If you don't provide an `onUpdateFilter`, **all** update events are accepted.

{{% alert title="Use with care" color="warning" %}}
Disabling default filters removes the generation-aware, finalizer-needed and marked-for-deletion
guarantees. If you still want part of that behavior, compose it explicitly using the static factory
methods on
[`InternalEventFilters`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/controller/InternalEventFilters.java)
inside your own filter.
{{% /alert %}}

## Non-informer based event sources

The filters described above apply only to informer-backed event sources, since they operate on the
*add*, *update* and *delete* events an informer receives from the Kubernetes API. Event sources that
are not backed by an informer — such as [`PollingEventSource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/polling/PollingEventSource.java)
and [`PerResourcePollingEventSource`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/polling/PerResourcePollingEventSource.java)
— have no filters. For those, it is up to your implementation to emit only the events that are
actually relevant, so that reconciliations are not triggered needlessly.

