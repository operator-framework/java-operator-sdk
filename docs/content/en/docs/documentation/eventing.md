---
title: Event sources and related topics
weight: 47
---

## Handling Related Events with Event Sources

See also
this [blog post](https://csviri.medium.com/java-operator-sdk-introduction-to-event-sources-a1aab5af4b7b)
.

Event sources are a relatively simple yet powerful and extensible concept to trigger controller
executions, usually based on changes to dependent resources. You typically need an event source
when you want your `Reconciler` to be triggered when something occurs to secondary resources
that might affect the state of your primary resource. This is needed because a given
`Reconciler` will only listen by default to events affecting the primary resource type it is
configured for. Event sources act as listen to events affecting these secondary resources so
that a reconciliation of the associated primary resource can be triggered when needed. Note that
these secondary resources need not be Kubernetes resources. Typically, when dealing with
non-Kubernetes objects or services, we can extend our operator to handle webhooks or websockets
or to react to any event coming from a service we interact with. This allows for very efficient
controller implementations because reconciliations are then only triggered when something occurs
on resources affecting our primary resources thus doing away with the need to periodically
reschedule reconciliations.

![Event Sources architecture diagram](/images/event-sources.png)

There are few interesting points here:

The `CustomResourceEventSource` event source is a special one, responsible for handling events
pertaining to changes affecting our primary resources. This `EventSource` is always registered
for every controller automatically by the SDK. It is important to note that events always relate
to a given primary resource. Concurrency is still handled for you, even in the presence of
`EventSource` implementations, and the SDK still guarantees that there is no concurrent execution of
the controller for any given primary resource (though, of course, concurrent/parallel executions
of events pertaining to other primary resources still occur as expected).

### Caching and Event Sources

Kubernetes resources are handled in a declarative manner. The same also holds true for event
sources. For example, if we define an event source to watch for changes of a Kubernetes Deployment
object using an `InformerEventSource`, we always receive the whole associated object from the
Kubernetes API. This object might be needed at any point during our reconciliation process and
it's best to retrieve it from the event source directly when possible instead of fetching it
from the Kubernetes API since the event source guarantees that it will provide the latest
version. Not only that, but many event source implementations also cache resources they handle
so that it's possible to retrieve the latest version of resources without needing to make any
calls to the Kubernetes API, thus allowing for very efficient controller implementations.

Note after an operator starts, caches are already populated by the time the first reconciliation
is processed for the `InformerEventSource` implementation. However, this does not necessarily
hold true for all event source implementations (`PerResourceEventSource` for example). The SDK
provides methods to handle this situation elegantly, allowing you to check if an object is
cached, retrieving it from a provided supplier if not. See
related [method](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/polling/PerResourcePollingEventSource.java#L146)
.

### Registering Event Sources

To register event sources, your `Reconciler` has to override the `prepareEventSources` and return
list of event sources to register. One way to see this in action is
to look at the
[WebPage example](https://github.com/operator-framework/java-operator-sdk/blob/main/sample-operators/webpage/src/main/java/io/javaoperatorsdk/operator/sample/WebPageReconciler.java)
(irrelevant details omitted):

```java

import java.util.List;

@ControllerConfiguration
public class WebappReconciler
        implements Reconciler<Webapp>, Cleaner<Webapp>, EventSourceInitializer<Webapp> {
    // ommitted code

    @Override
    public List<EventSource<?, Webapp>> prepareEventSources(EventSourceContext<Webapp> context) {
        InformerEventSourceConfiguration<Webapp> configuration =
                InformerEventSourceConfiguration.from(Deployment.class, Webapp.class)
                        .withLabelSelector(SELECTOR)
                        .build();
        return List.of(new InformerEventSource<>(configuration, context));
    }
}
```

In the example above an `InformerEventSource` is configured and registered.
`InformerEventSource` is one of the bundled `EventSource` implementations that JOSDK provides to
cover common use cases.

### Managing Relation between Primary and Secondary Resources

Event sources let your operator know when a secondary resource has changed and that your
operator might need to reconcile this new information. However, in order to do so, the SDK needs
to somehow retrieve the primary resource associated with which ever secondary resource triggered
the event. In the `Webapp` example above, when an event occurs on a tracked `Deployment`, the
SDK needs to be able to identify which `Webapp` resource is impacted by that change.

Seasoned Kubernetes users already know one way to track this parent-child kind of relationship:
using owner references. Indeed, that's how the SDK deals with this situation by default as well,
that is, if your controller properly set owner references on your secondary resources, the SDK
will be able to follow that reference back to your primary resource automatically without you
having to worry about it.

However, owner references cannot always be used as they are restricted to operating within a
single namespace (i.e. you cannot have an owner reference to a resource in a different namespace)
and are, by essence, limited to Kubernetes resources so you're out of luck if your secondary
resources live outside of a cluster.

This is why JOSDK provides the `SecondayToPrimaryMapper` interface so that you can provide
alternative ways for the SDK to identify which primary resource needs to be reconciled when
something occurs to your secondary resources. We even provide some of these alternatives in the
[Mappers](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/Mappers.java)
class.

Note that, while a set of `ResourceID` is returned, this set usually consists only of one
element. It is however possible to return multiple values or even no value at all to cover some
rare corner cases. Returning an empty set means that the mapper considered the secondary
resource event as irrelevant and the SDK will thus not trigger a reconciliation of the primary
resource in that situation.

Adding a `SecondaryToPrimaryMapper` is typically sufficient when there is a one-to-many relationship
between primary and secondary resources. The secondary resources can be mapped to its primary
owner, and this is enough information to also get these secondary resources from the `Context`
object that's passed to your `Reconciler`.

There are however cases when this isn't sufficient and you need to provide an explicit mapping
between a primary resource and its associated secondary resources using an implementation of the
`PrimaryToSecondaryMapper` interface. This is typically needed when there are many-to-one or
many-to-many relationships between primary and secondary resources, e.g. when the primary resource
is referencing secondary resources.
See [PrimaryToSecondaryIT](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/primarytosecondary/PrimaryToSecondaryIT.java)
integration test for a sample.

### Built-in EventSources

There are multiple event-sources provided out of the box, the following are some more central ones:

#### `InformerEventSource`

[InformerEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/informer/InformerEventSource.java)
is probably the most important `EventSource` implementation to know about. When you create an
`InformerEventSource`, JOSDK will automatically create and register a `SharedIndexInformer`, a
fabric8 Kubernetes client class, that will listen for events associated with the resource type
you configured your `InformerEventSource` with. If you want to listen to Kubernetes resource
events, `InformerEventSource` is probably the only thing you need to use. It's highly
configurable so you can tune it to your needs. Take a look at
[InformerEventSourceConfiguration](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/informer/InformerEventSourceConfiguration.java)
and associated classes for more details but some interesting features we can mention here is the
ability to filter events so that you can only get notified for events you care about. A
particularly interesting feature of the `InformerEventSource`, as opposed to using your own
informer-based listening mechanism is that caches are particularly well optimized preventing
reconciliations from being triggered when not needed and allowing efficient operators to be written.

#### `PerResourcePollingEventSource`

[PerResourcePollingEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/polling/PerResourcePollingEventSource.java)
is used to poll external APIs, which don't support webhooks or other event notifications. It
extends the abstract
[ExternalResourceCachingEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/ExternalResourceCachingEventSource.java)
to support caching.
See [MySQL Schema sample](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/mysql-schema/src/main/java/io/javaoperatorsdk/operator/sample/MySQLSchemaReconciler.java)
for usage.

#### `PollingEventSource`

[PollingEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/polling/PollingEventSource.java)
is similar to `PerResourceCachingEventSource` except that, contrary to that event source, it
doesn't poll a specific API separately per resource, but periodically and independently of
actually observed primary resources.

#### Inbound event sources

[SimpleInboundEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/inbound/SimpleInboundEventSource.java)
and
[CachingInboundEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/inbound/CachingInboundEventSource.java)
are used to handle incoming events from webhooks and messaging systems.

#### `ControllerResourceEventSource`

[ControllerResourceEventSource](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/source/controller/ControllerResourceEventSource.java)
is a special `EventSource` implementation that you will never have to deal with directly. It is,
however, at the core of the SDK is automatically added for you: this is the main event source
that listens for changes to your primary resources and triggers your `Reconciler` when needed.
It features smart caching and is really optimized to minimize Kubernetes API accesses and avoid
triggering unduly your `Reconciler`.

More on the philosophy of the non Kubernetes API related event source see in
issue [#729](https://github.com/java-operator-sdk/java-operator-sdk/issues/729).


## InformerEventSource Multi-Cluster Support

It is possible to handle resources for remote cluster with `InformerEventSource`. To do so,
simply set a client that connects to a remote cluster:

```java

InformerEventSourceConfiguration<WebPage> configuration =
        InformerEventSourceConfiguration.from(SecondaryResource.class, PrimaryResource.class)
            .withKubernetesClient(remoteClusterClient)
            .withSecondaryToPrimaryMapper(Mappers.fromDefaultAnnotations());

```

You will also need to specify a `SecondaryToPrimaryMapper`, since the default one
is based on owner references and won't work across cluster instances. You could, for example, use the provided implementation that relies on annotations added to the secondary resources to identify the associated primary resource.

See related [integration test](https://github.com/operator-framework/java-operator-sdk/tree/main/operator-framework/src/test/java/io/javaoperatorsdk/operator/baseapi/informerremotecluster).


## Generation Awareness and Event Filtering

A best practice when an operator starts up is to reconcile all the associated resources because
changes might have occurred to the resources while the operator was not running.

When this first reconciliation is done successfully, the next reconciliation is triggered if either
dependent resources are changed or the primary resource `.spec` field is changed. If other fields
like `.metadata` are changed on the primary resource, the reconciliation could be skipped. This
behavior is supported out of the box and reconciliation is by default not triggered if
changes to the primary resource do not increase the `.metadata.generation` field.
Note that changes to `.metada.generation` are automatically handled by Kubernetes.

To turn off this feature, set `generationAwareEventProcessing` to `false` for the `Reconciler`.


## Max Interval Between Reconciliations

When informers / event sources are properly set up, and the `Reconciler` implementation is
correct, no additional reconciliation triggers should be needed. However, it's
a [common practice](https://github.com/java-operator-sdk/java-operator-sdk/issues/848#issuecomment-1016419966)
to have a failsafe periodic trigger in place, just to make sure resources are nevertheless
reconciled after a certain amount of time. This functionality is in place by default, with a
rather high time interval (currently 10 hours) after which a reconciliation will be
automatically triggered even in the absence of other events. See how to override this using the
standard annotation:

```java
@ControllerConfiguration(maxReconciliationInterval = @MaxReconciliationInterval(
                interval = 50,
                timeUnit = TimeUnit.MILLISECONDS))
public class MyReconciler implements Reconciler<HasMetadata> {}
```

The event is not propagated at a fixed rate, rather it's scheduled after each reconciliation. So the
next reconciliation will occur at most within the specified interval after the last reconciliation.

This feature can be turned off by setting `maxReconciliationInterval`
to [`Constants.NO_MAX_RECONCILIATION_INTERVAL`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Constants.java#L20-L20)
or any non-positive number.

The automatic retries are not affected by this feature so a reconciliation will be re-triggered
on error, according to the specified retry policy, regardless of this maximum interval setting.

## Rate Limiting

It is possible to rate limit reconciliation on a per-resource basis. The rate limit also takes
precedence over retry/re-schedule configurations: for example, even if a retry was scheduled for
the next second but this request would make the resource go over its rate limit, the next
reconciliation will be postponed according to the rate limiting rules. Note that the
reconciliation is never cancelled, it will just be executed as early as possible based on rate
limitations.

Rate limiting is by default turned **off**, since correct configuration depends on the reconciler
implementation, in particular, on how long a typical reconciliation takes.
(The parallelism of reconciliation itself can be
limited  [`ConfigurationService`](https://github.com/java-operator-sdk/java-operator-sdk/blob/ce4d996ee073ebef5715737995fc3d33f4751275/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L120-L120)
by configuring the `ExecutorService` appropriately.)

A default rate limiter implementation is provided, see:
[`PeriodRateLimiter`](https://github.com/java-operator-sdk/java-operator-sdk/blob/ce4d996ee073ebef5715737995fc3d33f4751275/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/rate/PeriodRateLimiter.java#L14-L14)
.
Users can override it by implementing their own
[`RateLimiter`](https://github.com/java-operator-sdk/java-operator-sdk/blob/ce4d996ee073ebef5715737995fc3d33f4751275/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/rate/RateLimiter.java)
and specifying this custom implementation using the `rateLimiter` field of the
`@ControllerConfiguration` annotation. Similarly to the `Retry` implementations,
`RateLimiter` implementations must provide an accessible, no-arg constructor for instantiation
purposes and can further be automatically configured from your own, provided annotation provided
your `RateLimiter` implementation also implements the `AnnotationConfigurable` interface,
parameterized by your custom annotation type.

To configure the default rate limiter use the `@RateLimited` annotation on your
`Reconciler` class. The following configuration limits each resource to reconcile at most twice
within a 3 second interval:

```java

@RateLimited(maxReconciliations = 2, within = 3, unit = TimeUnit.SECONDS)
@ControllerConfiguration
public class MyReconciler implements Reconciler<MyCR> {

}
```

Thus, if a given resource was reconciled twice in one second, no further reconciliation for this
resource will happen before two seconds have elapsed. Note that, since rate is limited on a
per-resource basis, other resources can still be reconciled at the same time, as long, of course,
that they stay within their own rate limits.

## Optimizing Caches

One of the ideas around the operator pattern is that all the relevant resources are cached, thus reconciliation is
usually very fast (especially if no resources are updated in the process) since the operator is then mostly working with
in-memory state. However for large clusters, caching huge amount of primary and secondary resources might consume lots
of memory. JOSDK provides ways to mitigate this issue and optimize the memory usage of controllers. While these features
are working and tested, we need feedback from real production usage.

### Bounded Caches for Informers

Limiting caches for informers - thus for Kubernetes resources - is supported by ensuring that resources are in the cache
for a limited time, via a cache eviction of least recently used resources. This means that when resources are created
and frequently reconciled, they stay "hot" in the cache. However, if, over time, a given resource "cools" down, i.e. it
becomes less and less used to the point that it might not be reconciled anymore, it will eventually get evicted from the
cache to free up memory. If such an evicted resource were to become reconciled again, the bounded cache implementation
would then fetch it from the API server and the "hot/cold" cycle would start anew.

Since all resources need to be reconciled when a controller start, it is not practical to set a maximal cache size as
it's desirable that all resources be cached as soon as possible to make the initial reconciliation process on start as
fast and efficient as possible, avoiding undue load on the API server. It's therefore more interesting to gradually
evict cold resources than try to limit cache sizes.

See usage of the related implementation using [Caffeine](https://github.com/ben-manes/caffeine) cache in integration
tests
for [primary resources](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/caffeine-bounded-cache-support/src/test/java/io/javaoperatorsdk/operator/processing/event/source/cache/sample/AbstractTestReconciler.java).

See
also [CaffeineBoundedItemStores](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/caffeine-bounded-cache-support/src/main/java/io/javaoperatorsdk/operator/processing/event/source/cache/CaffeineBoundedItemStores.java)
for more details.