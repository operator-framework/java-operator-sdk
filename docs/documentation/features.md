---
title: Features
description: Features of the SDK
layout: docs
permalink: /docs/features
---

# Features 

## Controller Registration

## Configurations

## Finalizers

### When not to Use Finalizers

## Automatic Retries on Error

### Correctness and automatic retry

## Re-Scheduling Execution

## Retry and Re-Scheduling Common Behavior

## Handling Related Events with Event Sources

### Caching and Event Sources

### The CustomResourceEventSource

### Built-in Event Sources

## Monitoring with Micrometer

## Contextual Info for Logging with MDC

Logging is enhanced with additional contextual information using [MDC](http://www.slf4j.org/manual.html#mdc). 
When it is following contextual information is added to logging, it is available in most parts of reconciliation logic
and during the execution of the controller:

| MDC Key      | Value added from Custom Resource |
| :---        |    :---   | 
| `resource.apiVersion`   | `.apiVersion` |
| `resource.kind`   | `.kind` |
| `resource.name`      | `.metadata.name` | 
| `resource.namespace`   | `.metadata.namespace` |
| `resource.resourceVersion`   | `.metadata.resourceVersion` |
| `resource.generation`   | `.metadata.generation` |
| `resource.uid`   | `.metadata.uid` |

For more information about MDC see this [link](https://www.baeldung.com/mdc-in-log4j-2-logback).





