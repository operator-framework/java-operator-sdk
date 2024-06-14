---
title: Version 5.0.0
weight: 10
---

# Version 5.0.0-RC1 Released!

We've just released the next major version of Java Operator SDK. There is no single goal for this release, it is rather
a collection of improvements and features that required API changes. In this post we will go thourgh the major changes
and explain the rational behind them, also show how to migrate from previous release if needed.

## API Changes

### Removal of EventSourceInitializer interface

`EventSourceInitializer` is an interfaced that was implemented by most of the reconcilers. In general
we want to minimize such interaces, since it is hard to find them just "by looking at code", so this
interface was removed and `prepareEventSources(EventSourceContext<P> context)` was simply moved to
`Reconciler` interface with a default empty implementation.

So you can simply just delete this interface from you reconciler implementation.

```java

public class WebPageReconciler
    implements Reconciler<WebPage>, ErrorStatusHandler<WebPage>, E̶v̶e̶n̶t̶S̶o̶u̶r̶c̶e̶I̶n̶i̶t̶i̶a̶l̶i̶z̶e̶r̶<W̶e̶b̶P̶a̶g̶e̶> {

// omitted code

}

see related issue [here](https://github.com/operator-framework/java-operator-sdk/issues/2029).

```

This interface implemented also some utility methods, now those cane be found in [`EventSourceUtils`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/EventSourceUtils.java).


### Named event sources 

The name is now directly an attribute of an `EventSource`. EventSources were named also in previous releases but name was not an attribute. This leads mainly to better internal structures, also
solves issues out of the box, like when a Dependent Resource provides an event source some case it need to have specific name.

TODO






