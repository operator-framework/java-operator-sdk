---
title: Logging
weight: 72
---

## Contextual Info for Logging with MDC

Logging is enhanced with additional contextual information using
[MDC](http://www.slf4j.org/manual.html#mdc). The following attributes are available in most
parts of reconciliation logic and during the execution of the controller:

| MDC Key                    | Value added from primary resource |
|:---------------------------|:----------------------------------|
| `resource.apiVersion`      | `.apiVersion`                     |
| `resource.kind`            | `.kind`                           |
| `resource.name`            | `.metadata.name`                  |
| `resource.namespace`       | `.metadata.namespace`             |
| `resource.resourceVersion` | `.metadata.resourceVersion`       |
| `resource.generation`      | `.metadata.generation`            |
| `resource.uid`             | `.metadata.uid`                   |

For more information about MDC see this [link](https://www.baeldung.com/mdc-in-log4j-2-logback).

### MDC entries during event handling

Although, usually users might not require it in their day-to-day workflow, it is worth mentioning that
there are additional MDC entries managed for event handling. Typically, you might be interested in it
in your `SecondaryToPrimaryMapper` related logs.
For `InformerEventSource` and `ControllerEventSource` the following information is present:

| MDC Key                                        | Value from Resource from the Event               |
|:-----------------------------------------------|:-------------------------------------------------|
| `eventsource.event.resource.name`              | `.metadata.name`                                 |
| `eventsource.event.resource.uid`               | `.metadata.uid`                                  |
| `eventsource.event.resource.namespace`         | `.metadata.namespace`                            |
| `eventsource.event.resource.kind`              | resource kind                                    |
| `eventsource.event.resource.resourceVersion`   | `.metadata.resourceVersion`                      |
| `eventsource.event.action`                     | action name (e.g. `ADDED`, `UPDATED`, `DELETED`) |
| `eventsource.name`                             | name of the event source                         |

### Note on null values

If a resource doesn't provide values for one of the specified keys, the key will be omitted and not added to the MDC
context. There is, however, one notable exception: the resource's namespace, where, instead of omitting the key, we emit
the `MDCUtils.NO_NAMESPACE` value instead. This allows searching for resources without namespace (notably, clustered
resources) in the logs more easily.

### Disabling MDC support

MDC support is enabled by default. If you want to disable it, you can set the `JAVA_OPERATOR_SDK_USE_MDC` environment
variable to `false` when you start your operator.
