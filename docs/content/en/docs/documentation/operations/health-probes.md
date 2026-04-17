---
title: Health Probes
weight: 75
---

Operators running in Kubernetes should expose health probe endpoints so that the kubelet can detect startup
failures and runtime degradation. JOSDK provides the building blocks through its
[`RuntimeInfo`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/RuntimeInfo.java)
API.

## RuntimeInfo

`RuntimeInfo` is available via `operator.getRuntimeInfo()` and exposes:

| Method | Purpose |
|---|---|
| `isStarted()` | `true` once the operator and all its controllers have fully started |
| `allEventSourcesAreHealthy()` | `true` when every registered event source (informers, polling sources, etc.) reports a healthy status |
| `unhealthyEventSources()` | returns a map of controller name → unhealthy event sources, useful for diagnostics |
| `unhealthyInformerWrappingEventSourceHealthIndicator()` | returns a map of controller name → unhealthy informer-wrapping event sources, each exposing per-informer details via `InformerHealthIndicator` (`hasSynced()`, `isWatching()`, `isRunning()`, `getTargetNamespace()`) |

In most cases a single readiness probe backed by `allEventSourcesAreHealthy()` is sufficient: before the
operator has fully started the informers will not have synced yet, so the check naturally covers the startup
case as well. Once running, it detects runtime degradation such as a lost watch connection.

### Fine-Grained Informer Diagnostics

For advanced use cases — such as exposing per-informer health in a diagnostic endpoint or logging which
specific namespace lost its watch — `unhealthyInformerWrappingEventSourceHealthIndicator()` gives access to
individual `InformerHealthIndicator` instances. Each indicator exposes `hasSynced()`, `isWatching()`,
`isRunning()`, and `getTargetNamespace()`. This is typically not needed for a standard health probe but can
be valuable for operational dashboards or troubleshooting.

## Setting Up a Probe Endpoint

The example below uses [Jetty](https://eclipse.dev/jetty/) to expose a `/healthz` endpoint. Any HTTP
server library works — the key is calling the `RuntimeInfo` methods to determine the response code.

```java
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;

Operator operator = new Operator();
operator.register(new MyReconciler());

// start the health server before the operator so probes can be queried during startup
var health = new ContextHandler(new HealthHandler(operator), "/healthz");
Server server = new Server(8080);
server.setHandler(health);
server.start();

operator.start();
```

Where `HealthHandler` extends `org.eclipse.jetty.server.Handler.Abstract` and checks
`operator.getRuntimeInfo().allEventSourcesAreHealthy()`.

See the
[`operations` sample operator](https://github.com/java-operator-sdk/java-operator-sdk/tree/main/sample-operators/operations)
for a complete working example.

## Kubernetes Deployment Configuration

Once your operator exposes the probe endpoint, configure a readiness probe in your Deployment manifest:

```yaml
containers:
- name: operator
  ports:
  - name: probes
    containerPort: 8080
  readinessProbe:
    httpGet:
      path: /healthz
      port: probes
    initialDelaySeconds: 5
    periodSeconds: 5
    failureThreshold: 3
```

The readiness probe will mark the pod as not-ready until all informers have synced. After that, it
continues to monitor event source health at runtime.

## Helm Chart Support

The [generic Helm chart](/docs/documentation/operations/helm-chart) supports health probes out of the box.
Enable them in your `values.yaml`:

```yaml
probes:
  port: 8080
  readiness:
    enabled: true
    path: /healthz
```

All probe timing parameters (`initialDelaySeconds`, `periodSeconds`, `failureThreshold`) have sensible
defaults and can be overridden.
