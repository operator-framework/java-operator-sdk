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