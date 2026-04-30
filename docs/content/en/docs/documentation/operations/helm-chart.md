---
title: Generic Helm Chart
weight: 86
---

A generic, reusable Helm chart for deploying Java operators built with JOSDK is available at
[`helm/generic-helm-chart`](https://github.com/java-operator-sdk/java-operator-sdk/tree/main/helm/generic-helm-chart).

It is intended as a **template for operator developers** — a starting point that covers common deployment
patterns so you don't have to write a chart from scratch. The chart is maintained on a **best-effort basis**.
Contributions are more than welcome.

The chart is used in the
[`operations` sample operator E2E test](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/operations/src/test/java/io/javaoperatorsdk/operator/sample/operations/OperationsE2E.java)
to deploy the operator to a cluster via Helm.

## What the Chart Provides

- **Deployment** with security defaults (non-root user, read-only filesystem, no privilege escalation)
- **Dynamic RBAC** (ClusterRole, ClusterRoleBinding, ServiceAccount) — permissions are generated automatically
  from the primary and secondary resources you declare in `values.yaml`
- **ConfigMap** for operator configuration (`config.yaml`) and logging (`log4j2.xml`), mounted at `/config`
- **Leader election** support (opt-in)
- **Extensibility** via extra containers, init containers, volumes, and environment variables

## Key Configuration

The most important values to set when adapting the chart for your operator:

```yaml
image:
  repository: my-operator-image   # required
  tag: "latest"

# Custom resources your operator reconciles
primaryResources:
  - apiGroup: "sample.javaoperatorsdk"
    resources:
      - myresources

# Kubernetes resources your operator manages
secondaryResources:
  - apiGroup: ""
    resources:
      - configmaps
      - services
```

Primary resources get read/watch/patch permissions and status sub-resource access.
Secondary resources get full CRUD permissions. Default verbs can be overridden per resource entry.

### Operator Environment

The chart injects `OPERATOR_NAMESPACE` automatically. You can optionally set `WATCH_NAMESPACE` to
restrict the operator to a single namespace, and add arbitrary environment variables:

```yaml
operator:
  watchNamespace: ""       # empty = all namespaces
  env:
    - name: MY_CUSTOM_VAR
      value: "some-value"
```

### Resource Defaults

```yaml
resources:
  limits:
    cpu: 500m
    memory: 512Mi
  requests:
    cpu: 100m
    memory: 128Mi
```

See the full
[`values.yaml`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/helm/generic-helm-chart/values.yaml)
for all available options.

## Usage Example

A working example of how to use the chart can be found in the operations sample operator's
[`helm-values.yaml`](https://github.com/java-operator-sdk/java-operator-sdk/blob/main/sample-operators/operations/src/test/resources/helm-values.yaml):

```yaml
image:
  repository: operations-operator
  pullPolicy: Never
  tag: "latest"

nameOverride: "operations-operator"

resources: {}

primaryResources:
  - apiGroup: "sample.javaoperatorsdk"
    resources:
      - metricshandlingcustomresource1s
      - metricshandlingcustomresource2s
```

Install with:

```shell
helm install my-operator ./helm/generic-helm-chart -f my-values.yaml --namespace my-ns
```

## Testing the Chart

The chart includes unit tests using the [helm-unittest](https://github.com/helm-unittest/helm-unittest) plugin.
Run them with:

```shell
./helm/run-tests.sh
```
