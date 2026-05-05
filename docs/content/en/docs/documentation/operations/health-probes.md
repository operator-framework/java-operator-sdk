---
title: Health Probes
weight: 85
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
| `unhealthyInformerWrappingEventSourceHealthIndicator()` | returns a map of controller name → unhealthy informer-wrapping event sources, each exposing per-informer details via `InformerHealthIndicator` (`hasSynced()`, `isRunning()`, `getTargetNamespace()`) |

In most cases a single readiness probe backed by `allEventSourcesAreHealthy()` is sufficient: before the
operator has fully started the informers will not have synced yet, so the check naturally covers the startup
case as well. Once running, it detects runtime degradation such as a lost watch connection.

See also:
[ConfigurationService.stopOnInformerErrorDuringStartup](https://github.com/operator-framework/java-operator-sdk/blob/main/operator-framework-core/src/main/java/io/javaoperatorsdk/operator/api/config/ConfigurationService.java#L287)

### Fine-Grained Informer Diagnostics

For advanced use cases — such as exposing per-informer health in a diagnostic endpoint or logging which
specific namespace lost its watch — `unhealthyInformerWrappingEventSourceHealthIndicator()` gives access to
individual `InformerHealthIndicator` instances. Each indicator exposes `hasSynced()`,
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

Once your operator exposes the probe endpoint, configure probes in your Deployment manifest. Both the
startup and readiness probes can point to the same `/healthz` endpoint — the startup probe simply uses a
higher `failureThreshold` to give the operator time to initialize:

```yaml
containers:
- name: operator
  ports:
  - name: probes
    containerPort: 8080
  startupProbe:
    httpGet:
      path: /healthz
      port: probes
    initialDelaySeconds: 1
    periodSeconds: 3
    failureThreshold: 20
  readinessProbe:
    httpGet:
      path: /healthz
      port: probes
    initialDelaySeconds: 5
    periodSeconds: 5
    failureThreshold: 3
```

The startup probe gives the operator time to start (up to ~60 s with the settings above). Once the startup
probe succeeds, the readiness probe takes over and will mark the pod as not-ready if any event source
becomes unhealthy.

## Helm Chart Support

The [generic Helm chart](/docs/documentation/operations/helm-chart) supports health probes out of the box.
Enable them in your `values.yaml`:

```yaml
probes:
  port: 8080
  startup:
    enabled: true
    path: /healthz
  readiness:
    enabled: true
    path: /healthz
```

All probe timing parameters (`initialDelaySeconds`, `periodSeconds`, `failureThreshold`) have sensible
defaults and can be overridden.
