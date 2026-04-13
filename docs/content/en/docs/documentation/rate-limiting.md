---
title: Rate limiting
weight: 47
---

It is possible to rate limit reconciliation on a per-resource basis. The rate limit also takes
precedence over retry/re-schedule configurations: for example, even if retry would re-schedule a reconciliation
but this request would make the resource go over its rate limit, the next
reconciliation will be postponed according to the rate limiting rules. Note that the
reconciliation is never canceled, it will just be executed as early as possible based on rate
limitations.

Rate limiting is by default turned **off**, since correct configuration depends on the reconciler
implementation, in particular, on how long a typical reconciliation takes.
(The parallelism of reconciliation itself can be limited via
[`ConfigurationService`](https://github.com/java-operator-sdk/java-operator-sdk/blob/ce4d996ee073ebef5715737995fc3d33f4751275/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L120-L120)
by configuring the `ExecutorService` appropriately.)

We provide a generic rate limiter implementation:
[`LinearRateLimiter`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/rate/LinearRateLimiter.java)
.
You can provide your own rate limiter by implementing the [`RateLimiter`](https://github.com/java-operator-sdk/java-operator-sdk/blob/ce4d996ee073ebef5715737995fc3d33f4751275/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/event/rate/RateLimiter.java)
interface.

You can set this custom implementation using explicit [configuration](operations/configuration.md) or using the `rateLimiter`
field of the `@ControllerConfiguration` annotation. 
For the annotation approach, similarly to `Retry` implementations,
`RateLimiter` implementations must provide an accessible, no-arg constructor for instantiation
purposes and can further be automatically configured from your own annotation, provided that
your `RateLimiter` implementation also implements the `AnnotationConfigurable` interface,
parameterized by your custom annotation type.

To configure the default rate limiter, use the `@RateLimited` annotation on your
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
