---
title: Migrating from v5.2 to v5.3
description: Migrating from v5.2 to v5.3
---

## Automated Migration with OpenRewrite

You can automatically apply all the migration changes described below using [OpenRewrite](https://docs.openrewrite.org/).
Add the following to your `pom.xml` and run `mvn rewrite:run`:

```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>6.33.0</version>
  <configuration>
    <activeRecipes>
      <recipe>io.javaoperatorsdk.operator.migration.V5_3Migration</recipe>
    </activeRecipes>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>io.javaoperatorsdk</groupId>
      <artifactId>migration</artifactId>
      <version>5.3.0</version>
    </dependency>
  </dependencies>
</plugin>
```

## Rename of JUnit module

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

The following table shows the relevant method renames:

| v5.2 method                        | v5.3 method                  |
|------------------------------------|------------------------------|
| `reconcileCustomResource`          | `reconciliationSubmitted`    |
| `reconciliationExecutionStarted`   | `reconciliationStarted`      |
| `reconciliationExecutionFinished`  | `reconciliationSucceeded`    |
| `failedReconciliation`             | `reconciliationFailed`       |
| `finishedReconciliation`           | `reconciliationFinished`     |
| `cleanupDoneFor`                   | `cleanupDone`                |
| `receivedEvent`                    | `eventReceived`              |


Other changes:
- `reconciliationFinished(..)` method is extended with `RetryInfo`
- `monitorSizeOf(..)` method is removed.