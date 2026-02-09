# Observability Stack for Java Operator SDK

This directory contains the setup scripts and Grafana dashboards for monitoring Java Operator SDK applications.

## Installation

Run the installation script to deploy the full observability stack (OpenTelemetry Collector, Prometheus, and Grafana):

```bash
./install-observability.sh
```

This will install:
- **cert-manager** - Required for OpenTelemetry Operator
- **OpenTelemetry Operator** - Manages OpenTelemetry Collector instances
- **OpenTelemetry Collector** - Receives OTLP metrics and exports to Prometheus
- **Prometheus** - Metrics storage and querying
- **Grafana** - Metrics visualization

## Accessing Services

### Grafana
```bash
kubectl port-forward -n observability svc/kube-prometheus-stack-grafana 3000:80
```
Then open http://localhost:3000
- Username: `admin`
- Password: `admin`

### Prometheus
```bash
kubectl port-forward -n observability svc/kube-prometheus-stack-prometheus 9090:9090
```
Then open http://localhost:9090

## Grafana Dashboards

Two pre-configured dashboards are **automatically imported** during installation:

### 1. JVM Metrics Dashboard (`jvm-metrics-dashboard.json`)

Monitors Java Virtual Machine health and performance:

**Panels:**
- **JVM Memory Used** - Heap and non-heap memory consumption by memory pool
- **JVM Threads** - Live, daemon, and peak thread counts
- **GC Pause Time Rate** - Garbage collection pause duration
- **GC Pause Count Rate** - Frequency of garbage collection events
- **CPU Usage** - System CPU utilization percentage
- **Classes Loaded** - Number of classes currently loaded
- **Process Uptime** - Application uptime in seconds
- **CPU Count** - Available processor cores
- **GC Memory Allocation Rate** - Memory allocation and promotion rates
- **Heap Memory Max vs Committed** - Heap memory limits and commitments

**Key Metrics:**
- `jvm.memory.used`, `jvm.memory.max`, `jvm.memory.committed`
- `jvm.gc.pause`, `jvm.gc.memory.allocated`, `jvm.gc.memory.promoted`
- `jvm.threads.live`, `jvm.threads.daemon`, `jvm.threads.peak`
- `jvm.classes.loaded`, `jvm.classes.unloaded`
- `system.cpu.usage`, `system.cpu.count`
- `process.uptime`

### 2. Java Operator SDK Metrics Dashboard (`josdk-operator-metrics-dashboard.json`)

Monitors Kubernetes operator performance and health:

**Panels:**
- **Reconciliation Rate (Started)** - Rate of reconciliation loops triggered
- **Reconciliation Success vs Failure Rate** - Success/failure ratio over time
- **Currently Executing Reconciliations** - Active reconciliation threads
- **Reconciliation Queue Size** - Pending reconciliation work
- **Total Reconciliations** - Cumulative count of reconciliations
- **Error Rate** - Overall error rate across all reconciliations
- **Reconciliation Execution Time** - P50, P95, P99 latency percentiles
- **Event Reception Rate** - Kubernetes event processing rate
- **Failures by Exception Type** - Breakdown of errors by exception class
- **Controller Execution Success vs Failure** - Controller-level success metrics
- **Delete Event Rate** - Resource deletion event frequency
- **Reconciliation Retry Rate** - Retry attempts and patterns

**Key Metrics:**
- `operator.sdk.reconciliations.started`, `.success`, `.failed`
- `operator.sdk.reconciliations.executions` - Current execution count
- `operator.sdk.reconciliations.queue.size` - Queue depth
- `operator.sdk.controllers.execution.reconcile` - Execution timing histograms
- `operator.sdk.events.received`, `.delete` - Event reception
- Retry metrics and failure breakdowns

## Importing Dashboards into Grafana

### Automatic Import (Default)

The dashboards are **automatically imported** when you run `./install-observability.sh`. They will appear in Grafana within 30-60 seconds after installation. No manual steps required!

To verify the dashboards were imported:
1. Access Grafana at http://localhost:3000
2. Navigate to **Dashboards** → **Browse**
3. Look for "JOSDK - JVM Metrics" and "JOSDK - Operator Metrics"

### Manual Import Methods

If you need to re-import or update the dashboards manually:

#### Method 1: Via Grafana UI

1. Access Grafana at http://localhost:3000
2. Login with admin/admin
3. Navigate to **Dashboards** → **Import**
4. Click **Upload JSON file**
5. Select `jvm-metrics-dashboard.json` or `josdk-operator-metrics-dashboard.json`
6. Select **Prometheus** as the data source
7. Click **Import**

#### Method 2: Via kubectl ConfigMap

```bash
# Re-import JVM dashboard
kubectl create configmap jvm-metrics-dashboard \
  --from-file=jvm-metrics-dashboard.json \
  -n observability \
  -o yaml --dry-run=client | \
  kubectl label --dry-run=client --local -f - grafana_dashboard=1 -o yaml | \
  kubectl apply -f -

# Re-import Operator dashboard
kubectl create configmap josdk-operator-metrics-dashboard \
  --from-file=josdk-operator-metrics-dashboard.json \
  -n observability \
  -o yaml --dry-run=client | \
  kubectl label --dry-run=client --local -f - grafana_dashboard=1 -o yaml | \
  kubectl apply -f -
```

The dashboards will be automatically discovered and loaded by Grafana within 30-60 seconds.

## Configuring Your Operator

To enable metrics export from your JOSDK operator, ensure your application:

1. **Has the required dependency** (already included in webpage sample):
   ```xml
   <dependency>
     <groupId>io.micrometer</groupId>
     <artifactId>micrometer-registry-otlp</artifactId>
   </dependency>
   ```

2. **Configures OTLP export** via `otlp-config.yaml`:
   ```yaml
   otlp:
     url: "http://otel-collector-collector.observability.svc.cluster.local:4318/v1/metrics"
     step: 15s
     batchSize: 15000
     aggregationTemporality: "cumulative"
   ```

3. **Registers JVM and JOSDK metrics** (see `WebPageOperator.java` for reference implementation)

## OTLP Endpoints

The OpenTelemetry Collector provides the following endpoints:

- **OTLP gRPC**: `otel-collector-collector.observability.svc.cluster.local:4317`
- **OTLP HTTP**: `otel-collector-collector.observability.svc.cluster.local:4318`
- **Prometheus Scrape**: `http://otel-collector-prometheus.observability.svc.cluster.local:8889/metrics`

## Troubleshooting

### Check OpenTelemetry Collector Logs
```bash
kubectl logs -n observability -l app.kubernetes.io/name=otel-collector -f
```

### Check Prometheus Targets
```bash
kubectl port-forward -n observability svc/kube-prometheus-stack-prometheus 9090:9090
```
Open http://localhost:9090/targets and verify the OTLP collector target is UP.

### Verify Metrics in Prometheus
Open Prometheus UI and search for metrics:
- JVM metrics: `otel_jvm_*`
- Operator metrics: `otel_operator_sdk_*`

### Check Grafana Data Source
1. Navigate to **Configuration** → **Data Sources**
2. Verify Prometheus data source is configured and working
3. Click **Test** to verify connectivity

## Uninstalling

To remove the observability stack:

```bash
kubectl delete configmap -n observability jvm-metrics-dashboard josdk-operator-metrics-dashboard
kubectl delete -n observability OpenTelemetryCollector otel-collector
helm uninstall -n observability kube-prometheus-stack
helm uninstall -n observability opentelemetry-operator
helm uninstall -n cert-manager cert-manager
kubectl delete namespace observability cert-manager
```

## Customizing Dashboards

The dashboard JSON files can be modified to:
- Add new panels for custom metrics
- Adjust time ranges and refresh intervals
- Change visualization types
- Add templating variables for filtering
- Modify alert thresholds

After making changes, re-import the dashboard using one of the methods above.

## Example Queries

### JVM Metrics
```promql
# Heap memory usage percentage
(otel_jvm_memory_used_bytes{area="heap"} / otel_jvm_memory_max_bytes{area="heap"}) * 100

# GC throughput (percentage of time NOT in GC)
100 - (rate(otel_jvm_gc_pause_seconds_sum[5m]) * 100)

# Thread count trend
otel_jvm_threads_live_threads
```

### Operator Metrics
```promql
# Reconciliation success rate
rate(otel_operator_sdk_reconciliations_success_total[5m]) / rate(otel_operator_sdk_reconciliations_started_total[5m])

# Average reconciliation time
rate(otel_operator_sdk_controllers_execution_reconcile_seconds_sum[5m]) / rate(otel_operator_sdk_controllers_execution_reconcile_seconds_count[5m])

# Queue saturation
otel_operator_sdk_reconciliations_queue_size / on() group_left() max(otel_operator_sdk_reconciliations_queue_size)
```

## References

- [Java Operator SDK Documentation](https://javaoperatorsdk.io)
- [Micrometer OTLP Documentation](https://micrometer.io/docs/registry/otlp)
- [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/)
- [Grafana Dashboards](https://grafana.com/docs/grafana/latest/dashboards/)
