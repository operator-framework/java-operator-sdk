---
title: Error handling and retries
weight: 46
---

## Automatic Retries on Error

JOSDK will schedule an automatic retry of the reconciliation whenever an exception is thrown by
your `Reconciler`. The retry is behavior is configurable but a default implementation is provided
covering most of the typical use-cases, see
[GenericRetry](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/retry/GenericRetry.java)
.

```java
    GenericRetry.defaultLimitedExponentialRetry()
        .setInitialInterval(5000)
        .setIntervalMultiplier(1.5D)
        .setMaxAttempts(5);
```

You can also configure the default retry behavior using the `@GradualRetry` annotation.

It is possible to provide a custom implementation using the `retry` field of the
`@ControllerConfiguration` annotation and specifying the class of your custom implementation.
Note that this class will need to provide an accessible no-arg constructor for automated
instantiation. Additionally, your implementation can be automatically configured from an
annotation that you can provide by having your `Retry` implementation implement the
`AnnotationConfigurable` interface, parameterized with your annotation type. See the
`GenericRetry` implementation for more details.

Information about the current retry state is accessible from
the [Context](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Context.java)
object. Of note, particularly interesting is the `isLastAttempt` method, which could allow your
`Reconciler` to implement a different behavior based on this status, by setting an error message
in your resource' status, for example, when attempting a last retry.

Note, though, that reaching the retry limit won't prevent new events to be processed. New
reconciliations will happen for new events as usual. However, if an error also occurs that
would normally trigger a retry, the SDK won't schedule one at this point since the retry limit
is already reached.

A successful execution resets the retry state.

### Setting Error Status After Last Retry Attempt

In order to facilitate error reporting, `Reconciler` can implement the
[ErrorStatusHandler](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ErrorStatusHandler.java)
interface:

```java
public interface ErrorStatusHandler<P extends HasMetadata> {

   ErrorStatusUpdateControl<P> updateErrorStatus(P resource, Context<P> context, Exception e);

}
```

The `updateErrorStatus` method is called in case an exception is thrown from the `Reconciler`. It is
also called even if no retry policy is configured, just after the reconciler execution.
`RetryInfo.getAttemptCount()` is zero after the first reconciliation attempt, since it is not a
result of a retry (regardless of whether a retry policy is configured or not).

`ErrorStatusUpdateControl` is used to tell the SDK what to do and how to perform the status
update on the primary resource, always performed as a status sub-resource request. Note that
this update request will also produce an event, and will result in a reconciliation if the
controller is not generation aware.

This feature is only available for the `reconcile` method of the `Reconciler` interface, since
there should not be updates to resource that have been marked for deletion.

Retry can be skipped in cases of unrecoverable errors:

```java
 ErrorStatusUpdateControl.patchStatus(customResource).withNoRetry();
```

### Correctness and Automatic Retries

While it is possible to deactivate automatic retries, this is not desirable, unless for very
specific reasons. Errors naturally occur, whether it be transient network errors or conflicts
when a given resource is handled by a `Reconciler` but is modified at the same time by a user in
a different process. Automatic retries handle these cases nicely and will usually result in a
successful reconciliation.

## Retry and Rescheduling and Event Handling Common Behavior

Retry, reschedule and standard event processing form a relatively complex system, each of these
functionalities interacting with the others. In the following, we describe the interplay of
these features:

1. A successful execution resets a retry and the rescheduled executions which were present before
   the reconciliation. However, a new rescheduling can be instructed from the reconciliation
   outcome (`UpdateControl` or `DeleteControl`).

   For example, if a reconciliation had previously been re-scheduled after some amount of time, but an event triggered
   the reconciliation (or cleanup) in the mean time, the scheduled execution would be automatically cancelled, i.e.
   re-scheduling a reconciliation does not guarantee that one will occur exactly at that time, it simply guarantees that
   one reconciliation will occur at that time at the latest, triggering one if no event from the cluster triggered one.
   Of course, it's always possible to re-schedule a new reconciliation at the end of that "automatic" reconciliation.

   Similarly, if a retry was scheduled, any event from the cluster triggering a successful execution in the mean time
   would cancel the scheduled retry (because there's now no point in retrying something that already succeeded)

2. In case an exception happened, a retry is initiated. However, if an event is received
   meanwhile, it will be reconciled instantly, and this execution won't count as a retry attempt.
3. If the retry limit is reached (so no more automatic retry would happen), but a new event
   received, the reconciliation will still happen, but won't reset the retry, and will still be
   marked as the last attempt in the retry info. The point (1) still holds, but in case of an
   error, no retry will happen.

The thing to keep in mind when it comes to retrying or rescheduling is that JOSDK tries to avoid unnecessary work. When
you reschedule an operation, you instruct JOSDK to perform that operation at the latest by the end of the rescheduling
delay. If something occurred on the cluster that triggers that particular operation (reconciliation or cleanup), then
JOSDK considers that there's no point in attempting that operation again at the end of the specified delay since there
is now no point to do so anymore. The same idea also applies to retries.