---
title: Migrating from v4.2 to v4.3
description: Migrating from v4.2 to v4.3
layout: docs
permalink: /docs/v4-3-migration
---

# Migrating from v4.2 to v4.3

## Condition API Change

In Workflows the target of the condition was the managed resource itself, not a dependent resource. This changed, from
not the API contains the dependent resource.

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

Migration is trivial. Since the secondary resource can be accessed from the dependent resource. So to access the secondary
resource just use `dependentResource.getSecondaryResource(primary,context)`.
