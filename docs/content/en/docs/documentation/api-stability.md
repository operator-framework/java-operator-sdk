---
title: Public API and versioning
weight: 58
---

The Java Operator SDK (JOSDK) follows [semantic versioning](https://semver.org/). The
guarantees that come with semantic versioning &mdash; most importantly, that no breaking changes are
introduced in minor or patch releases &mdash; apply to the **public API** only. To make it explicit
which parts of the codebase are part of that public API, JOSDK uses a small set of source-level
annotations.

## `@Public`

Types that are part of the public API are marked with
[`@Public`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Public.java).
Such API is subject to semantic versioning: it will not be changed in a backwards-incompatible way
within a major version.

Marking a type with `@Public` is enough &mdash; it means that all of its public members (methods and
fields) are part of the public API. There is no need to annotate the individual members.

```java
@Public
public interface Reconciler<P extends HasMetadata> {
  // ...
}
```

## `@Internal`

Everything that is **not** marked with `@Public` should be considered internal: it may change or be
removed at any time, in any release, and should not be relied upon by end users.

The
[`@Internal`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Internal.java)
annotation is used as an *exception marker*: it flags a member (or nested type) of an otherwise
`@Public` type as internal, signalling that the member is not covered by the semantic-versioning
guarantees even though the enclosing type is. It is not meant to be put on every internal class
&mdash; internal classes are simply left without the `@Public` annotation.

A typical case is a public type that has to expose framework callbacks (for example, methods invoked
by the underlying informer or by the framework's eventing machinery) which operator authors are never
expected to call themselves:

```java
@Public
public class InformerEventSource<R extends HasMetadata, P extends HasMetadata> // ...
    implements ResourceEventHandler<R> {

  @Override
  @Internal // called by the informer, not by end users
  public void onAdd(R newResource) {
    // ...
  }
}
```

## `@Experimental`

[`@Experimental`](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/Experimental.java)
marks API that is available and intended to be maintained, but is not yet stable. Such API may still
change between releases &mdash; usually based on user feedback &mdash; even though it is technically
reachable. Experimental API is therefore **not** subject to the semantic-versioning guarantees and is
not marked `@Public`.

## Note for contributors

When you add new API that is meant to be used by operator authors, annotate the type with `@Public`
so that it becomes part of the versioning contract. If a public type exposes a member that is only
there for internal use, mark that member with `@Internal`. Leave purely internal classes unannotated.
