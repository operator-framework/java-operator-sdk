---
title: Migrating from v4.7 to v5.0
description: Migrating from v4.7 to v5.0
layout: docs
permalink: /docs/v5-0-migration
---

# Migrating from v4.7 to v5.0

## API Tweaks

1. [Result of managed dependent resources](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/managed/ManagedDependentResourceContext.java#L55-L57)
   is not `Optional` anymore. In case you use this result, simply use the result
   objects directly.

2. Workflow is now explicit for managed dependent resources:
   So instead (from WebPage sample):

```java

@ControllerConfiguration(
    dependents = {
        @Dependent(type = ConfigMapDependentResource.class),
        @Dependent(type = DeploymentDependentResource.class),
        @Dependent(type = ServiceDependentResource.class),
        @Dependent(type = IngressDependentResource.class,
            reconcilePrecondition = ExposedIngressCondition.class)
    }))
//  Omitted code
```


   Now the following structure is used:
   
```java
@ControllerConfiguration(
    workflow = @Workflow(dependents = {
        @Dependent(type = ConfigMapDependentResource.class),
        @Dependent(type = DeploymentDependentResource.class),
        @Dependent(type = ServiceDependentResource.class),
        @Dependent(type = IngressDependentResource.class,
            reconcilePrecondition = ExposedIngressCondition.class)
    }))
//  Omitted code      
```