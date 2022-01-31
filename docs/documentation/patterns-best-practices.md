---
title: Patterns and Best Practices
description: Patterns and Best Practices Implementing a Controller
layout: docs
permalink: /docs/patterns-best-practices
---

# Patterns and Best Practices

This document describes patters and best practices, to build and run operators, and how to implement them in terms of
Java Operator SDK.

See also best practices in [Operator SDK](https://sdk.operatorframework.io/docs/best-practices/best-practices/).

## Implementing a Reconciler

### Reconcile All The Resources All the Time

The reconciliation can be triggered by events from multiple sources. It could be tempting to check the events, and
reconcile just the related resource or subset of resources that controller manages. However, this is **considered as an
anti-pattern** in operators. If triggered, all resources should be reconciled. Note that usually this means only
comparing the target state with the current state in the cache. The reason behind this is events not reliable in
general, thus means events can be lost.

In addition to that such approach might even complicate implementation logic in the `Reconciler`, since parallel
execution of the reconciler is not allowed for the same custom resource, there can be multiple events received for the
same resource or dependent resource during an ongoing execution, ordering those events could be also challenging.

For this reason from v2 the events are not even accessible for the `Reconciler`.

### Idempotency

Since all the resources are reconciled during an execution and an execution can be triggered quite often, also 
retries of a reconciliation can happen naturally in operators, the implementation of a `Reconciler` 
needs to be idempotent. Luckily, since operators are usually managing already declarative resources, this is trivial
to do in most cases.

### Sync or Async Way of Resource Handling

In an implementation of reconciliation there can be a point when reconciler needs to wait a non-insignificant amount
of time while a resource gets up and running. For example, reconciler would do some additional step only if a Pod is ready
to receive requests. This problem can  in two ways synchronously or asynchronously. 


## Why to Have Automated Retries?

## Managing State

## Dependent Resources

### EventSources and Caching

### Why are Events Irrelevant?

