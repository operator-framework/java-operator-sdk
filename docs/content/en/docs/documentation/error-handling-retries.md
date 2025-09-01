---
title: Error handling and retries
weight: 46
---

## How Automatic Retries Work

JOSDK automatically schedules retries whenever your `Reconciler` throws an exception. This robust retry mechanism helps handle transient issues like network problems or temporary resource unavailability.

### Default Retry Behavior

The default retry implementation covers most typical use cases with exponential backoff:

```java
GenericRetry.defaultLimitedExponentialRetry()
    .setInitialInterval(5000)       // Start with 5-second delay
    .setIntervalMultiplier(1.5D)    // Increase delay by 1.5x each retry
    .setMaxAttempts(5);             // Maximum 5 attempts
```

### Configuration Options

**Using the `@GradualRetry` annotation:**

```java
@ControllerConfiguration
@GradualRetry(maxAttempts = 3, initialInterval = 2000)
public class MyReconciler implements Reconciler<MyResource> {
    // reconciler implementation
}
```

**Custom retry implementation:**

Specify a custom retry class in the `@ControllerConfiguration` annotation:

```java
@ControllerConfiguration(retry = MyCustomRetry.class)
public class MyReconciler implements Reconciler<MyResource> {
    // reconciler implementation
}
```

Your custom retry class must:
- Provide a no-argument constructor for automatic instantiation
- Optionally implement `AnnotationConfigurable` for configuration from annotations. See [`GenericRetry`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/processing/retry/GenericRetry.java#) 
  implementation for more details.

### Accessing Retry Information

The [Context](https://github.com/java-operator-sdk/java-operator-sdk/blob/master/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Context.java) object provides retry state information:

```java
@Override
public UpdateControl<MyResource> reconcile(MyResource resource, Context<MyResource> context) {
    if (context.isLastAttempt()) {
        // Handle final retry attempt differently
        resource.getStatus().setErrorMessage("Failed after all retry attempts");
        return UpdateControl.patchStatus(resource);
    }
    
    // Normal reconciliation logic
    // ...
}
```

### Important Retry Behavior Notes

- **Retry limits don't block new events**: When retry limits are reached, new reconciliations still occur for new events
- **No retry on limit reached**: If an error occurs after reaching the retry limit, no additional retries are scheduled until new events arrive
- **Event-driven recovery**: Fresh events can restart the retry cycle, allowing recovery from previously failed states

A successful execution resets the retry state.

### Reconciler Error Handler

In order to facilitate error reporting you can override [`updateErrorStatus`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/Reconciler.java#L52)
method in `Reconciler`:

```java
public class MyReconciler implements Reconciler<WebPage> {

   @Override
   public ErrorStatusUpdateControl<WebPage> updateErrorStatus(
           WebPage resource, Context<WebPage> context, Exception e) {
      return handleError(resource, e);
   }

}
```

The `updateErrorStatus` method is called in case an exception is thrown from the `Reconciler`. It is
also called even if no retry policy is configured, just after the reconciler execution.
`RetryInfo.getAttemptCount()` is zero after the first reconciliation attempt, since it is not a
result of a retry (regardless of whether a retry policy is configured).

`ErrorStatusUpdateControl` tells the SDK what to do and how to perform the status
update on the primary resource, which is always performed as a status sub-resource request. Note that
this update request will also produce an event and result in a reconciliation if the
controller is not generation-aware.

This feature is only available for the `reconcile` method of the `Reconciler` interface, since
there should not be updates to resources that have been marked for deletion.

Retry can be skipped in cases of unrecoverable errors:

```java
 ErrorStatusUpdateControl.patchStatus(customResource).withNoRetry();
```

### Correctness and Automatic Retries

While it is possible to deactivate automatic retries, this is not desirable unless there is a particular reason.
Errors naturally occur, whether it be transient network errors or conflicts
when a given resource is handled by a `Reconciler` but modified simultaneously by a user in
a different process. Automatic retries handle these cases nicely and will eventually result in a
successful reconciliation.

## Retry, Rescheduling and Event Handling Common Behavior

Retry, reschedule, and standard event processing form a relatively complex system, each of these
functionalities interacting with the others. In the following, we describe the interplay of
these features:

1. A successful execution resets a retry and the rescheduled executions that were present before
   the reconciliation. However, the reconciliation outcome can instruct a new rescheduling (`UpdateControl` or `DeleteControl`).

   For example, if a reconciliation had previously been rescheduled for after some amount of time, but an event triggered
   the reconciliation (or cleanup) in the meantime, the scheduled execution would be automatically cancelled, i.e.
   rescheduling a reconciliation does not guarantee that one will occur precisely at that time; it simply guarantees that a reconciliation will occur at the latest.
   Of course, it's always possible to reschedule a new reconciliation at the end of that "automatic" reconciliation.

   Similarly, if a retry was scheduled, any event from the cluster triggering a successful execution in the meantime
   would cancel the scheduled retry (because there's now no point in retrying something that already succeeded)

2. In case an exception is thrown, a retry is initiated. However, if an event is received
   meanwhile, it will be reconciled instantly, and this execution won't count as a retry attempt.
3. If the retry limit is reached (so no more automatic retry would happen), but a new event
   received, the reconciliation will still happen, but won't reset the retry, and will still be
   marked as the last attempt in the retry info. The point (1) still holds - thus successful reconciliation will reset the retry - but no retry will happen in case of an error.
   
The thing to remember when it comes to retrying or rescheduling is that JOSDK tries to avoid unnecessary work. When
you reschedule an operation, you instruct JOSDK to perform that operation by the end of the rescheduling
delay at the latest. If something occurred on the cluster that triggers that particular operation (reconciliation or cleanup), then
JOSDK considers that there's no point in attempting that operation again at the end of the specified delay since there
is no point in doing so anymore. The same idea also applies to retries.
