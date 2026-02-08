# Observability Stack for Java Operator SDK

This directory contains scripts and configuration for setting up a complete observability stack on minikube.

## Quick Start

```bash
./install-observability.sh
```

This script installs:
- **OpenTelemetry Operator** - For collecting metrics and traces
- **Prometheus** - For metrics storage and querying
- **Grafana** - For visualization and dashboards
- **cert-manager** - Required for OpenTelemetry Operator webhooks

## Prerequisites

- kubectl configured
- Helm 3.x installed

## Components Installed

### OpenTelemetry Collector
- Receives metrics and traces via OTLP (gRPC and HTTP)
- Exports metrics to Prometheus format
- Configured with memory limiter and batch processing

**Endpoints:**
- OTLP gRPC: `otel-collector-collector.observability.svc.cluster.local:4317`
- OTLP HTTP: `otel-collector-collector.observability.svc.cluster.local:4318`
- Prometheus metrics: `http://otel-collector-prometheus.observability.svc.cluster.local:8889/metrics`

### Prometheus
- Scrapes metrics from OpenTelemetry Collector
- Supports ServiceMonitor and PodMonitor CRDs
- Configured to discover all metrics automatically

**Access:**
```bash
kubectl port-forward -n observability svc/kube-prometheus-stack-prometheus 9090:9090
```
Open http://localhost:9090

### Grafana
- Pre-configured with Prometheus as data source
- Includes Kubernetes monitoring dashboards

**Access:**
```bash
kubectl port-forward -n observability svc/kube-prometheus-stack-grafana 3000:80
```
Open http://localhost:3000
- **Username:** admin
- **Password:** admin

## Integrating with Your Operator

### 1. Add OpenTelemetry Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework-opentelemetry-support</artifactId>
    <version>${josdk.version}</version>
</dependency>
```

### 2. Configure OpenTelemetry in Your Operator

In your operator code:

```java
import io.javaoperatorsdk.operator.monitoring.opentelemetry.OpenTelemetryMetrics;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

// Initialize OpenTelemetry
OpenTelemetry openTelemetry = AutoConfiguredOpenTelemetrySdk.initialize()
    .getOpenTelemetrySdk();

// Create JOSDK metrics instance
Metrics metrics = OpenTelemetryMetrics.builder(openTelemetry)
    .build();

// Configure operator with metrics
Operator operator = new Operator(client, o -> o.withMetrics(metrics));
```

### 3. Set Environment Variables

In your operator deployment YAML:

```yaml
env:
  - name: OTEL_SERVICE_NAME
    value: "your-operator-name"
  - name: OTEL_EXPORTER_OTLP_ENDPOINT
    value: "http://otel-collector-collector.observability.svc.cluster.local:4318"
  - name: OTEL_METRICS_EXPORTER
    value: "otlp"
  - name: OTEL_TRACES_EXPORTER
    value: "otlp"
  - name: OTEL_EXPORTER_OTLP_PROTOCOL
    value: "http/protobuf"
```

## Available JOSDK Metrics

The following metrics are exported by JOSDK:

| Metric | Type | Description |
|--------|------|-------------|
| `operator_sdk_reconciliations_started_total` | Counter | Total number of reconciliations started |
| `operator_sdk_reconciliations_success_total` | Counter | Total number of successful reconciliations |
| `operator_sdk_reconciliations_failed_total` | Counter | Total number of failed reconciliations |
| `operator_sdk_reconciliations_queue_size` | Gauge | Current reconciliation queue size |
| `operator_sdk_events_received_total` | Counter | Total number of Kubernetes events received |
| `operator_sdk_controllers_execution_reconcile_seconds` | Timer | Time taken for reconciliations |
| `operator_sdk_controllers_execution_cleanup_seconds` | Timer | Time taken for cleanup operations |

## Creating Grafana Dashboards

### Example PromQL Queries

**Reconciliation Rate:**
```promql
sum(rate(operator_sdk_reconciliations_started_total[5m])) by (controller)
```

**Success Rate:**
```promql
sum(rate(operator_sdk_reconciliations_success_total[5m])) /
sum(rate(operator_sdk_reconciliations_started_total[5m]))
```

**Error Rate:**
```promql
sum(rate(operator_sdk_reconciliations_failed_total[5m])) by (controller, exception)
```

**Queue Size:**
```promql
operator_sdk_reconciliations_queue_size
```

**Average Reconciliation Duration:**
```promql
rate(operator_sdk_controllers_execution_reconcile_seconds_sum[5m]) /
rate(operator_sdk_controllers_execution_reconcile_seconds_count[5m])
```

### Sample Dashboard Configuration

1. Open Grafana (http://localhost:3000)
2. Go to "Dashboards" → "New Dashboard"
3. Add panels with the PromQL queries above
4. Configure visualization types:
   - Time series for rates and durations
   - Gauge for queue size
   - Stat for current values

## Troubleshooting

### Check Pod Status
```bash
kubectl get pods -n observability
```

### Check OpenTelemetry Collector Logs
```bash
kubectl logs -n observability -l app.kubernetes.io/name=otel-collector -f
```

### Check Prometheus Targets
```bash
kubectl port-forward -n observability svc/kube-prometheus-stack-prometheus 9090:9090
```
Then open http://localhost:9090/targets

### Verify Metrics are Being Collected
```bash
# Check if OpenTelemetry is receiving metrics
kubectl port-forward -n observability svc/otel-collector-prometheus 8889:8889
curl http://localhost:8889/metrics | grep operator_sdk
```

### Test OTLP Endpoint
```bash
# Port forward the OTLP HTTP endpoint
kubectl port-forward -n observability svc/otel-collector-collector 4318:4318

# Send a test metric (requires curl and valid OTLP JSON)
# This is just for testing connectivity
curl -X POST http://localhost:4318/v1/metrics \
  -H "Content-Type: application/json" \
  -d '{"resourceMetrics":[]}'
```

## Uninstalling

To remove all components:

```bash
# Delete OpenTelemetry resources
kubectl delete -n observability OpenTelemetryCollector otel-collector

# Uninstall Helm releases
helm uninstall -n observability kube-prometheus-stack
helm uninstall -n observability opentelemetry-operator
helm uninstall -n cert-manager cert-manager

# Delete namespaces
kubectl delete namespace observability cert-manager
```

## References

- [JOSDK Observability Documentation](https://javaoperatorsdk.io/docs/documentation/observability/)
- [OpenTelemetry Java Documentation](https://opentelemetry.io/docs/instrumentation/java/)
- [Prometheus Operator](https://github.com/prometheus-operator/prometheus-operator)
- [Grafana Documentation](https://grafana.com/docs/)
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/)
