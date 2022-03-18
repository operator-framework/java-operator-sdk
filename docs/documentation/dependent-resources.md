---
title: Dependent Resources Feature
description: Dependent Resources Feature
layout: docs
permalink: /docs/dependent-resources
---

# Dependent Resources

DISCLAIMER: Dependent Resources is relatively new and implementation wise not a simple concept, some APIs could be still
a subject of change in the future. However, in case of non-backwards compatible change is expected to be trivial to
migrate.

## Motivations and Goals

During a reconciliation controller is managing other "secondary" resources. Typically, the following steps are executed:

1. Check if the desired resource already exists (usually if already present in the cache of an EventSource).
2. If the resource not exists create one with the desired spec.
3. If resource already exists, check if the spec of the resources is the same as desired.
    1. If the actual spec is different from the desired spec update it.

The process how the resource is read, created, updated or the desired state is compared to the actual state in
Kubernetes API are all generic sub-problems in a reconciler implementation. 
The motivation behind Dependent Resource is hide the mentioned problems (and much
more) from the developer and provide a system that allows managing the secondary resources in an
elegant and structured way.

## Design

## Managed Dependent Resources

## Standalone Dependent Resources

## Other Problems Handled by Dependent Resources