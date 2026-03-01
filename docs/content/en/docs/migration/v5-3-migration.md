---
title: Migrating from v5.2 to v5.3
description: Migrating from v5.2 to v5.3
---


## Renamed JUnit Module

If you use JUnit extension in your test just rename it from:

```
<dependency>
      <groupId>io.javaoperatorsdk</groupId>
      <artifactId>operator-framework-junit-5</artifactId>
      <version>5.2.x<version>
      <scope>test</scope>
</dependency>
```

to

```
<dependency>
      <groupId>io.javaoperatorsdk</groupId>
      <artifactId>operator-framework-junit</artifactId>
      <version>5.3.0<version>
      <scope>test</scope>
</dependency>
```


## Metrics interface changes

The [Metrics](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/monitoring/Metrics.java) 
interface changed in non backwards compatible way, in order to make the API cleaner:

The following table show the relevant changes:

1. `reconcileCustomResource` -> `submittedForReconciliation`
2. `reconciliationExecutionStarted` -> `reconciliationStarted` 
3. `reconciliationExecutionFinished` -> `successfulReconciliation`
4. `cleanupDoneFor` -> `cleanupDone`

Other changes:

- `finishedReconciliation(..)` method was extended with `RetryInfo`
