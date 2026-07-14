---
title: Migrating from v5.4 to v5.5
description: Migrating from v5.4 to v5.5
---

## No breaking API changes

v5.5 does **not** contain breaking API changes: existing code compiles and continues to work without
modification. There is, however, one **behavioral** change around own-event filtering that you should
be aware of (see below).

## `UpdateControl` no longer filters own events by default

In previous versions, updating the primary resource (or its status) by returning an `UpdateControl`
from the reconciler would filter out the event caused by that own update, so the write did not
trigger an additional reconciliation. Unfortunately, in some edge cases this could lead an 
incorrect behavior, thus filtering out events which should be propagated.

More precisely in case where for example the spec part of the primary resource was updated, while
controller patched the status, but that patch status was a no-op operation. Note that
these no-op operations are causing issue, which should not be done in first place.
From 5.5 we provide methods in `ResourceOperations` which allow only operations which are 
correct.

From v5.5, `UpdateControl` **no longer filters these own events by default**. Returning an
`UpdateControl` still updates the resource and keeps the cache read-after-write consistent, but the
resulting update event is now delivered like any other event, which may cause an additional
reconciliation.

This is safe (reconciliations are expected to be idempotent), but if you relied on the previous
filtering — for example to avoid an extra reconciliation after a status update — use
[`ResourceOperations`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/ResourceOperations.java)
directly, which does filter own events.

### Using `ResourceOperations` instead

`ResourceOperations` is available from the reconciliation `Context` via
`context.resourceOperations()`. Instead of returning an `UpdateControl`, perform the update through
it and return `UpdateControl.noUpdate()`:

```java
// before (v5.4): the own status update event was filtered
@Override
public UpdateControl<MyResource> reconcile(MyResource resource, Context<MyResource> context) {
  resource.setStatus(new MyStatus().setReady(true));
  return UpdateControl.patchStatus(resource);
}
```

```java
// after (v5.5): filter the own event explicitly via ResourceOperations
@Override
public UpdateControl<MyResource> reconcile(MyResource resource, Context<MyResource> context) {
  resource.setStatus(new MyStatus().setReady(true));
  context.resourceOperations().serverSideApplyPrimaryStatus(resource);
  return UpdateControl.noUpdate();
}
```

`ResourceOperations` covers every update/patch strategy (server-side apply, update, JSON Patch, JSON
Merge Patch) for both the whole resource and the status subresource, as well as their primary
variants. By default these operations match the desired state against the actual (cached) state
before writing and filter the own event, so they only issue a request to the Kubernetes API server
when something actually changed.

> **Note**: Safe own-event filtering requires either a matcher (used by default) or the update to be
> done with optimistic locking. See the `ResourceOperations` and `ResourceOperations.Options`
> documentation for the available modes, the correctness requirements, and the caveats of the
> default matchers.
