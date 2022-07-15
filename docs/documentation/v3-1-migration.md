---
title: Migrating from v3 to v3.1
description: Migrating from v3 to v3.1
layout: docs
permalink: /docs/v3-1-migration
---

# Migrating from v3 to v3.1

## Workflows Impact on Managed Dependent Resources Behavior

Version 3.1 comes with a workflow engine that replaces the previous behavior of managed dependent resources.
The primary impact after upgrade, is that if there is a list of managed dependent resource, for now those
were reconciled in order, in the new version are reconciled in parallel. Use 
['depends_on'](https://github.com/java-operator-sdk/java-operator-sdk/blob/df44917ef81725c10bbcb772ab7b434d511b13b9/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/reconciler/dependent/Dependent.java#L23-L23)
relation to define order between resources if needed.

## Garbage Collected Kubernetes Dependent Resources 

