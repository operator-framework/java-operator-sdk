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
echo -e "${GREEN}Prometheus + Grafana${NC}"
echo -e "${GREEN}========================================${NC}"

# Check if helm is installed, download locally if not
echo -e "\n${YELLOW}Checking helm installation...${NC}"
if ! command -v helm &> /dev/null; then
    echo -e "${YELLOW}helm not found, downloading locally...${NC}"
    HELM_INSTALL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/.helm"
    mkdir -p "$HELM_INSTALL_DIR"
    HELM_BIN="$HELM_INSTALL_DIR/helm"
    if [ ! -f "$HELM_BIN" ]; then
        curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \
            | HELM_INSTALL_DIR="$HELM_INSTALL_DIR" USE_SUDO=false bash
    fi
    export PATH="$HELM_INSTALL_DIR:$PATH"
    echo -e "${GREEN}✓ helm downloaded to $HELM_BIN${NC}"
else
    echo -e "${GREEN}✓ helm is installed${NC}"
fi

# Add Helm repositories
echo -e "\n${YELLOW}Adding Helm repositories...${NC}"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
echo -e "${GREEN}✓ Helm repositories added${NC}"

# Create observability namespace
echo -e "\n${YELLOW}Creating observability namespace...${NC}"
kubectl create namespace observability --dry-run=client -o yaml | kubectl apply -f -
echo -e "${GREEN}✓ observability namespace ready${NC}"

# Install kube-prometheus-stack (includes Prometheus + Grafana)
echo -e "\n${YELLOW}Installing Prometheus and Grafana stack...${NC}"
if helm list -n observability | grep -q kube-prometheus-stack; then
    echo -e "${YELLOW}kube-prometheus-stack already installed, upgrading...${NC}"
    helm upgrade kube-prometheus-stack prometheus-community/kube-prometheus-stack \
        --namespace observability \
        --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
        --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
        --set grafana.adminPassword=admin
else
    helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
        --namespace observability \
        --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
        --set prometheus.prometheusSpec.podMonitorSelectorNilUsesHelmValues=false \
        --set grafana.adminPassword=admin
fi
echo -e "${GREEN}✓ Prometheus and Grafana installation started${NC}"

# Wait for all pods to be ready
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}All resources have been applied!${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\n${YELLOW}Waiting for all pods to become ready (this may take 2-3 minutes)...${NC}"

echo -e "${YELLOW}Checking observability pods...${NC}"
kubectl wait --for=condition=ready pod --all -n observability --timeout=300s

echo -e "${GREEN}✓ All pods are ready${NC}"

# Import Grafana dashboards
echo -e "\n${YELLOW}Importing Grafana dashboards...${NC}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -f "$SCRIPT_DIR/jvm-metrics-dashboard.json" ]; then
    kubectl create configmap jvm-metrics-dashboard \
        --from-file="$SCRIPT_DIR/jvm-metrics-dashboard.json" \
        -n observability \
        --dry-run=client -o yaml | \
    kubectl label --dry-run=client --local -f - grafana_dashboard=1 -o yaml | \
    kubectl apply -f -
    echo -e "${GREEN}✓ JVM Metrics dashboard imported${NC}"
else
    echo -e "${YELLOW}⚠ JVM Metrics dashboard not found at $SCRIPT_DIR/jvm-metrics-dashboard.json${NC}"
fi

if [ -f "$SCRIPT_DIR/josdk-operator-metrics-dashboard.json" ]; then
    kubectl create configmap josdk-operator-metrics-dashboard \
        --from-file="$SCRIPT_DIR/josdk-operator-metrics-dashboard.json" \
        -n observability \
        --dry-run=client -o yaml | \
    kubectl label --dry-run=client --local -f - grafana_dashboard=1 -o yaml | \
    kubectl apply -f -
    echo -e "${GREEN}✓ JOSDK Operator Metrics dashboard imported${NC}"
else
    echo -e "${YELLOW}⚠ JOSDK Operator Metrics dashboard not found at $SCRIPT_DIR/josdk-operator-metrics-dashboard.json${NC}"
fi

echo -e "${GREEN}✓ Dashboards will be available in Grafana shortly${NC}"

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

echo -e "\n${YELLOW}Configure your Java Operator metrics:${NC}"
echo -e "  Add dependency: ${GREEN}io.javaoperatorsdk:micrometer-support${NC}"
echo -e "  Add dependency: ${GREEN}io.micrometer:micrometer-registry-prometheus${NC}"
echo -e "  Expose a ${GREEN}/metrics${NC} endpoint using PrometheusMeterRegistry"
echo -e "  Create a ServiceMonitor to let Prometheus scrape your operator"

echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}Grafana Dashboards${NC}"
echo -e "${GREEN}========================================${NC}"
echo -e "\nAutomatically imported dashboards:"
echo -e "  - ${GREEN}JOSDK - JVM Metrics${NC} - Java Virtual Machine health and performance"
echo -e "  - ${GREEN}JOSDK - Operator Metrics${NC} - Kubernetes operator performance and reconciliation"
echo -e "\nPre-installed Kubernetes dashboards:"
echo -e "  - Kubernetes / Compute Resources / Cluster"
echo -e "  - Kubernetes / Compute Resources / Namespace (Pods)"
echo -e "  - Node Exporter / Nodes"
echo -e "\n${YELLOW}Note:${NC} Dashboards may take 30-60 seconds to appear in Grafana after installation."

echo -e "\n${YELLOW}To uninstall:${NC}"
echo -e "  kubectl delete configmap -n observability jvm-metrics-dashboard josdk-operator-metrics-dashboard"
echo -e "  helm uninstall -n observability kube-prometheus-stack"
echo -e "  kubectl delete namespace observability"

echo -e "\n${GREEN}Done!${NC}"
