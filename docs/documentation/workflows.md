---
title: Workflows
description: Reference Documentation for Workflows
layout: docs
permalink: /docs/workflows
---

## Overview

Kubernetes (k8s) does not have notion of a resource "depends on" on another k8s resource,
in terms of in what order a set of resources should be reconciled. However, Kubernetes operators are used to manage also
external (non 8s) resources. Typically, when an operator manages a service, where after the service is first deployed 
some additional API calls are required to configure it. In this case the configuration step depends
on the service and related resources, in other words the configuration needs to be reconciled after the service is 
up and running. 

The intention behind workflows is to make it easy to describe more complex, almost arbitrary scenarios in a declarative
way. While [dependent resources](https://javaoperatorsdk.io/docs/dependent-resources) describes a logic how a single
resources should be reconciled, workflows describes the process how a set of target resources should be reconciled in 
a generic way. 

Workflows are defined as a set of [dependent resources](https://javaoperatorsdk.io/docs/dependent-resources) (DR) 
and dependencies between them, along with some conditions that mainly helps define optional resources and 
pre- and post-conditions to describe expected states of a resource at a certain point in the workflow.    


## Elements of Workflow 

- **Dependent resource** (DR) - are the resources which are reconciled.
- **Depends on relation** - if a DR B depends on another DR A, means that B will be reconciled after A is successfully 
  reconciled. 
- **Reconcile precondition** - is a condition that needs to be fulfilled before the DR is reconciled. This allows also
  to define optional resources, that for example only created if a flag in a custom resource `.spec` has some 
  specific value.
- **Ready postcondition** - checks if a resource could be considered "ready", typically if pods of a deployment are up
  and running.
- **Delete postcondition** - during the cleanup phase it can be used to check if the resources is successfully deleted,
   so the next resource on which the target resources depends can be deleted as next step.  

## Defining Workflows

As for dependent resource there are two ways to define workflows, in managed and standalone way.

### Managed


### Standalone 


## Reconciliation 

[//]: # (todo mention parallelism)

## Cleanup

## Notes

- Workflows can be seen as a Directed Acyclic Graph (DAG) - or more precisely a set of DAGs - where nodes are the 
dependent resources and edges are the dependencies.  

[//]: # (ready vs precondition)
