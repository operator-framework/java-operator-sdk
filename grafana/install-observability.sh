#!/bin/bash
#
# Copyright Java Operator SDK Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Installing Observability Stack${NC}"
echo -e "${GREEN}OpenTelemetry + Prometheus + Grafana${NC}"
echo -e "${GREEN}========================================${NC}"

# Check if minikube is running
echo -e "\n${YELLOW}Checking minikube status...${NC}"
if ! minikube status > /dev/null 2>&1; then
    echo -e "${RED}Error: minikube is not running${NC}"
    echo "Please start minikube with: minikube start"
    exit 1
fi
echo -e "${GREEN}✓ minikube is running${NC}"

# Check if helm is installed
echo -e "\n${YELLOW}Checking helm installation...${NC}"
if ! command -v helm &> /dev/null; then
    echo -e "${RED}Error: helm is not installed${NC}"
    echo "Please install helm: https://helm.sh/docs/intro/install/"
    exit 1
fi
echo -e "${GREEN}✓ helm is installed${NC}"

# Add Helm repositories
echo -e "\n${YELLOW}Adding Helm repositories...${NC}"
helm repo add jetstack https://charts.jetstack.io
helm repo add open-telemetry https://open-telemetry.github.io/opentelemetry-helm-charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
echo -e "${GREEN}✓ Helm repositories added${NC}"

# Install cert-manager (required for OpenTelemetry Operator)
echo -e "\n${YELLOW}Installing cert-manager...${NC}"
if kubectl get namespace cert-manager > /dev/null 2>&1; then
    echo -e "${YELLOW}cert-manager namespace already exists, skipping...${NC}"
else
    kubectl create namespace cert-manager
    helm install cert-manager jetstack/cert-manager \
        --namespace cert-manager \
        --set crds.enabled=true \
        --wait
    echo -e "${GREEN}✓ cert-manager installed${NC}"
fi

# Create observability namespace
echo -e "\n${YELLOW}Creating observability namespace...${NC}"
kubectl create namespace observability --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✓ observability namespace ready${NC}"

# Install OpenTelemetry Operator
echo -e "\n${YELLOW}Installing OpenTelemetry Operator...${NC}"
if helm list -n observability | grep -q opentelemetry-operator; then
    echo -e "${YELLOW}OpenTelemetry Operator already installed, upgrading...${NC}"
    helm upgrade opentelemetry-operator open-telemetry/opentelemetry-operator \
        --namespace observability \
        --set "manager.collectorImage.repository=otel/opentelemetry-collector-contrib" \
        --wait
else
    helm install opentelemetry-operator open-telemetry/opentelemetry-operator \
        --namespace observability \
        --set "manager.collectorImage.repository=otel/opentelemetry-collector-contrib" \
        --wait
fi
echo -e "${GREEN}✓ OpenTelemetry Operator installed${NC}"

# Install kube-prometheus-stack (includes Prometheus + Grafana)
echo -e "\n${YELLOW}Installing Prometheus and Grafana stack...${NC}"
if helm list -n observability | grep -q kube-prometheus-stack; then
    echo -e "${YELLOW}kube-prometheus-stack already installed, upgrading...${NC}"
    helm upgrade kube-prometheus-stack prometheus-community/kube-prometheus-stack \
        --namespace observability \
        --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
        --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
        --set grafana.adminPassword=admin \
        --wait
else
    helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
        --namespace observability \
        --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
        --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
        --set grafana.adminPassword=admin \
        --wait
fi
echo -e "${GREEN}✓ Prometheus and Grafana installed${NC}"

# Create OpenTelemetry Collector instance
echo -e "\n${YELLOW}Creating OpenTelemetry Collector...${NC}"
kubectl apply -f - <<EOF
apiVersion: opentelemetry.io/v1beta1
kind: OpenTelemetryCollector
metadata:
  name: otel-collector
  namespace: observability
spec:
  mode: deployment
  config:
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318
      prometheus:
        config:
          scrape_configs:
            - job_name: 'otel-collector'
              scrape_interval: 10s
              static_configs:
                - targets: ['0.0.0.0:8888']

    processors:
      batch:
        timeout: 10s
      memory_limiter:
        check_interval: 1s
        limit_percentage: 75
        spike_limit_percentage: 15

    exporters:
      prometheus:
        endpoint: "0.0.0.0:8889"
        namespace: "otel"
        send_timestamps: true
        metric_expiration: 5m
      debug:
        verbosity: detailed
        sampling_initial: 5
        sampling_thereafter: 200

    service:
      pipelines:
        metrics:
          receivers: [otlp, prometheus]
          processors: [memory_limiter, batch]
          exporters: [prometheus, debug]
        traces:
          receivers: [otlp]
          processors: [memory_limiter, batch]
          exporters: [debug]
EOF
echo -e "${GREEN}✓ OpenTelemetry Collector created${NC}"

# Create ServiceMonitor for OpenTelemetry Collector
echo -e "\n${YELLOW}Creating ServiceMonitor for OpenTelemetry...${NC}"
kubectl apply -f - <<EOF
apiVersion: v1
kind: Service
metadata:
  name: otel-collector-prometheus
  namespace: observability
  labels:
    app: otel-collector
spec:
  ports:
  - name: prometheus
    port: 8889
    targetPort: 8889
    protocol: TCP
  selector:
    app.kubernetes.io/name: otel-collector
---
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: otel-collector
  namespace: observability
  labels:
    app: otel-collector
spec:
  selector:
    matchLabels:
      app: otel-collector
  endpoints:
  - port: prometheus
    interval: 30s
EOF
echo -e "${GREEN}✓ ServiceMonitor created${NC}"

# Wait for all pods to be ready
echo -e "\n${YELLOW}Waiting for all pods to be ready...${NC}"
kubectl wait --for=condition=ready pod --all -n observability --timeout=300s
echo -e "${GREEN}✓ All pods are ready${NC}"

# Get pod statuses
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Installation Complete!${NC}"
echo -e "${GREEN}========================================${NC}"

echo -e "\n${YELLOW}Pod Status:${NC}"
kubectl get pods -n observability

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Access Information${NC}"
echo -e "${GREEN}========================================${NC}"

echo -e "\n${YELLOW}Grafana:${NC}"
echo -e "  Username: ${GREEN}admin${NC}"
echo -e "  Password: ${GREEN}admin${NC}"
echo -e "  Access with: ${GREEN}kubectl port-forward -n observability svc/kube-prometheus-stack-grafana 3000:80${NC}"
echo -e "  Then open: ${GREEN}http://localhost:3000${NC}"

echo -e "\n${YELLOW}Prometheus:${NC}"
echo -e "  Access with: ${GREEN}kubectl port-forward -n observability svc/kube-prometheus-stack-prometheus 9090:9090${NC}"
echo -e "  Then open: ${GREEN}http://localhost:9090${NC}"

echo -e "\n${YELLOW}OpenTelemetry Collector:${NC}"
echo -e "  OTLP gRPC endpoint: ${GREEN}otel-collector-collector.observability.svc.cluster.local:4317${NC}"
echo -e "  OTLP HTTP endpoint: ${GREEN}otel-collector-collector.observability.svc.cluster.local:4318${NC}"
echo -e "  Prometheus metrics: ${GREEN}http://otel-collector-prometheus.observability.svc.cluster.local:8889/metrics${NC}"

echo -e "\n${YELLOW}Configure your Java Operator to use OpenTelemetry:${NC}"
echo -e "  Add dependency: ${GREEN}io.javaoperatorsdk:operator-framework-opentelemetry-support${NC}"
echo -e "  Set environment variables:"
echo -e "    ${GREEN}OTEL_SERVICE_NAME=your-operator-name${NC}"
echo -e "    ${GREEN}OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector-collector.observability.svc.cluster.local:4318${NC}"
echo -e "    ${GREEN}OTEL_METRICS_EXPORTER=otlp${NC}"
echo -e "    ${GREEN}OTEL_TRACES_EXPORTER=otlp${NC}"

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Grafana Dashboards${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\nPre-installed dashboards in Grafana:"
echo -e "  - Kubernetes / Compute Resources / Cluster"
echo -e "  - Kubernetes / Compute Resources / Namespace (Pods)"
echo -e "  - Node Exporter / Nodes"
echo -e "\nFor JOSDK metrics, create a custom dashboard with queries like:"
echo -e "  ${GREEN}sum(rate(operator_sdk_reconciliations_started_total[5m]))${NC}"
echo -e "  ${GREEN}sum(rate(operator_sdk_reconciliations_success_total[5m]))${NC}"
echo -e "  ${GREEN}sum(rate(operator_sdk_reconciliations_failed_total[5m]))${NC}"

echo -e "\n${YELLOW}To uninstall:${NC}"
echo -e "  kubectl delete -n observability OpenTelemetryCollector otel-collector"
echo -e "  helm uninstall -n observability kube-prometheus-stack"
echo -e "  helm uninstall -n observability opentelemetry-operator"
echo -e "  helm uninstall -n cert-manager cert-manager"
echo -e "  kubectl delete namespace observability cert-manager"

echo -e "\n${GREEN}Done!${NC}"
