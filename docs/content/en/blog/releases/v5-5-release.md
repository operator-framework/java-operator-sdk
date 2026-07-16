---
title: Version 5.5 Released!
date: 2026-07-17
author: >-
  [Attila Mészáros](https://github.com/csviri)
---

We're pleased to announce the release of Java Operator SDK v5.5.0! This minor version reworks how
the framework filters the events caused by a controller's own writes, making it more correct and
more efficient, and exposes a richer, matcher-aware `ResourceOperations` API. There are **no
breaking API changes**, but there is one behavioral change around `UpdateControl` — see the
migration notes.

If you are running on version `5.3.x` or `5.4.x` upgrade is strongly recommend!

## Key Features

### Matcher-based updates in `ResourceOperations`

`ResourceOperations` (available from the reconciliation `Context` via `context.resourceOperations()`)
now offers a complete, consistent family of update/patch/create methods for every write strategy —
**server-side apply, update (PUT), JSON Patch (RFC 6902) and JSON Merge Patch (RFC 7386)** — each
available for the whole resource and the `status` subresource, and with dedicated `primary`
variants.

Every method comes in two flavors: a default one, and one taking an `Options` argument that controls
how the resulting own event is handled:

```java
public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) {
  makeStatusChanges(webPage);
  // updates the status, filters the own event, and skips the write entirely if nothing changed
  context.resourceOperations().serverSideApplyPrimaryStatus(webPage);
  return UpdateControl.noUpdate();
}
```

By default these operations **match the desired state against the actual (cached) state before
writing**: if they already match, the write is skipped; otherwise the write is performed and its own
event is filtered. This is the most efficient option — it covers full event filtering *and* avoids a
request to the Kubernetes API server when nothing changed. Default matchers are provided for every
operation type; when one does not fit, supply your own via `Options.matchAndFilter(matcher)`.

`Options` exposes the available strategies:

- `matchAndFilter(...)` / `matchAndFilterWithDefaultMatcher(...)` — match, then write-and-filter only
  if needed. This is the default for the server-side apply and patch methods.
- `filterWithOptimisticLocking()` — filter the own event; the write must use optimistic locking,
  otherwise an `IllegalArgumentException` is thrown. The matcher / `updateType` overloads also skip
  the write when the desired state already matches; this match + optimistic locking combination is
  the default for the PUT `update` methods.
- `cacheOnly()` — only cache the response (read-cache-after-write consistency), no filtering.
- `forceFilterEvents()` — always filter (mostly internal usage). Assumes resource was matched before
   or update is done using optimistic locking.

> **Correctness note**: filtering an own event is only safe if the framework can tell an own write
> apart from a concurrent third-party write. This requires **either** a matcher **or** optimistic
> locking; otherwise a concurrent external update inside the filter window may be missed until the
> next resync.

### More correct own-event filtering (no-op update edge case)

The read-cache-after-write own-event filter has been reworked to fix an edge case where a legitimate
external change could be filtered out together with a controller's own **no-op** update — for
example, when the spec was changed externally while the controller patched its status and that status
patch turned out to be a no-op. The new matcher-based operations avoid issuing such no-op writes in
the first place, and the filtering itself is now correct in these concurrent scenarios.

## Additional Improvements

- **`GenericKubernetesResourceMatcher.matchStatus(...)`**: a status-only counterpart to `match(...)`
  that compares just the `/status` subtree.
- **New `Matcher` SPI**: `io.javaoperatorsdk.operator.api.reconciler.matcher.Matcher` lets you plug a
  custom matching strategy into `Options.matchAndFilter(...)`.
- **Quieter logs for reconciler-handled errors**: when `updateErrorStatus(...)` returns any
  `ErrorStatusUpdateControl` other than `defaultErrorProcessing()`, the error is treated as handled
  by the reconciler and logged at `DEBUG` instead of the `Uncaught error during event processing`
  `WARN`. Native retry (including `@GradualRetry` exponential backoff) is unchanged, so a reconciler
  can keep retrying a recoverable condition without spamming warnings. Return
  `defaultErrorProcessing()` to keep the previous behavior.
- **Startup-latency metric**: a new `Metrics.eventProcessingStarted(Controller)` callback fires when
  a controller's event processor begins accepting events (including after a deferred start such as
  winning leader election). The Micrometer implementations record a per-controller gauge with the JVM
  uptime at that moment, making operator startup latency measurable.
- Extensive integration tests covering every `ResourceOperations` update/patch operation (primary,
  status, and secondary resources) against a real cluster.

## Migration Notes

There are **no breaking API changes**; existing code compiles and runs unchanged. However:

### `UpdateControl` no longer filters own events by default

Returning an `UpdateControl` (or `ErrorStatusUpdateControl`) still updates the resource and keeps the
cache read-after-write consistent, but it **no longer filters the resulting own event by default** —
so the write may cause an additional reconciliation (which should be idempotent). This change fixes correctness edge
cases where an event that should have propagated was previously swallowed.

If you relied on the previous filtering, perform the update through `ResourceOperations` and return
`UpdateControl.noUpdate()`:

```java
// before (v5.4)
resource.setStatus(new MyStatus().setReady(true));
return UpdateControl.patchStatus(resource);

// after (v5.5) — filter the own event explicitly
resource.setStatus(new MyStatus().setReady(true));
context.resourceOperations().serverSideApplyPrimaryStatus(resource);
return UpdateControl.noUpdate();
```

See the [migration guide](/docs/migration/v5-5-migration) for details.

## Getting Started

```xml
<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework</artifactId>
    <version>5.5.0</version>
</dependency>
```

## All Changes

See the [comparison view](https://github.com/operator-framework/java-operator-sdk/compare/v5.4.0...v5.5.0)
for the full list of changes.

## Feedback

Please report issues or suggest improvements on our
[GitHub repository](https://github.com/operator-framework/java-operator-sdk/issues).

Happy operator building! 🚀
