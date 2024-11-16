---
title: Migrating from v4.2 to v4.3
layout: docs
permalink: /docs/v4-3-migration
---

## Condition API Change

In Workflows the target of the condition was the managed resource itself, not the target dependent resource.
This changed, now the API contains the dependent resource.

New API:

```java
public interface Condition<R, P extends HasMetadata> {

    boolean isMet(DependentResource<R, P> dependentResource, P primary, Context<P> context);

}
```

Former API:

```java
public interface Condition<R, P extends HasMetadata> {

    boolean isMet(P primary, R secondary, Context<P> context);

}
```

Migration is trivial. Since the secondary resource can be accessed from the dependent resource. So to access the
secondary
resource just use `dependentResource.getSecondaryResource(primary,context)`.

## HTTP client choice

It is now possible to change the HTTP client used by the Fabric8 client to communicate with the Kubernetes API server.
By default, the SDK uses the historical default HTTP client which relies on Okhttp and there shouldn't be anything
needed to keep using this implementation. The `tomcat-operator` sample has been migrated to use the Vert.X based
implementation. You can see how to change the client by looking at
that [sample POM file](https://github.com/java-operator-sdk/java-operator-sdk/blob/d259fcd084f7e22032dfd0df3c7e64fe68850c1b/sample-operators/tomcat-operator/pom.xml#L37-L50):

- You need to exclude the default implementation (in this case okhttp) from the `operator-framework` dependency
- You need to add the appropriate implementation dependency, `kubernetes-httpclient-vertx` in this case, HTTP client
  implementations provided as part of the Fabric8 client all following the `kubernetes-httpclient-<implementation name>`
  pattern for their artifact identifier.