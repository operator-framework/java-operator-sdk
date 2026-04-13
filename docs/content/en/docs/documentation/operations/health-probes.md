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

These map naturally to Kubernetes probes:

- **Startup probe** → `isStarted()` — fails until all informers have synced and the operator is ready to
  reconcile.
- **Readiness probe** → `allEventSourcesAreHealthy()` — fails if an informer loses its watch connection
  or any event source reports an unhealthy status.

## Setting Up Probe Endpoints

The example below uses [Jetty](https://eclipse.dev/jetty/) to expose health probe endpoints. Any HTTP
server library works — the key is calling the `RuntimeInfo` methods to determine the response code.

```java
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;

Operator operator = new Operator();
operator.register(new MyReconciler());
operator.start();

var startup = new ContextHandler(new StartupHandler(operator), "/startup");
var readiness = new ContextHandler(new ReadinessHandler(operator), "/ready");
Server server = new Server(8080);
server.setHandler(new ContextHandlerCollection(startup, readiness));
server.start();
```

Where `StartupHandler` and `ReadinessHandler` extend `org.eclipse.jetty.server.Handler.Abstract` and
check `operator.getRuntimeInfo().isStarted()` and
`operator.getRuntimeInfo().allEventSourcesAreHealthy()` respectively.

See the
[`operations` sample operator](https://github.com/java-operator-sdk/java-operator-sdk/tree/main/sample-operators/operations)
for a complete working example.

## Kubernetes Deployment Configuration

Once your operator exposes probe endpoints, configure them in your Deployment manifest:

```yaml
containers:
- name: operator
  ports:
  - name: probes
    containerPort: 8080
  startupProbe:
    httpGet:
      path: /startup
      port: probes
    initialDelaySeconds: 1
    periodSeconds: 3
    failureThreshold: 20
  readinessProbe:
    httpGet:
      path: /ready
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
    path: /startup
  readiness:
    enabled: true
    path: /ready
```

All probe timing parameters (`initialDelaySeconds`, `periodSeconds`, `failureThreshold`) have sensible
defaults and can be overridden.
