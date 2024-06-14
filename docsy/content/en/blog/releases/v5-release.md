---
title: Version 5.0.0
weight: 10
---

# Version 5.0.0-RC1 Released!

We've just released the next major version of Java Operator SDK. There is no single goal for this release, it is rather
a collection of improvements and features that required API changes. In this post we will go thourgh the major changes
and explain the rational behind them, also show how to migrate from previous release if needed.

## API Changes

### Removal of `EventSourceInitializer` interafe

`EventSourceInitializer` is an interfaced that was implemented by most of the reconcilers. In general
we want to minimize such interaces, since it is hard to find them just "by looking at code", so this
interface was removed and `prepareEventSources(EventSourceContext<P> context)` was simply moved to
`Reconciler` interface with a default empty implementation.

So you can simply just delete this interface from you reconciler implementation.

```java

public class WebPageReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, ~~EventSourceInitializer<WebPage>~~ {


}

```








