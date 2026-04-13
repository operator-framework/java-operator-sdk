---
title: Leader Election
weight: 84
---

When running multiple replicas of an operator for high availability, leader election ensures that
only one instance actively reconciles resources at a time. JOSDK uses Kubernetes
[Lease](https://kubernetes.io/docs/concepts/architecture/leases/) objects for leader election.

## Enabling Leader Election

### Programmatic Configuration

```java
var operator = new Operator(o -> o.withLeaderElectionConfiguration(
    new LeaderElectionConfiguration("my-operator-lease", "operator-namespace")));
```

Or using the builder for full control:

```java
import static io.javaoperatorsdk.operator.api.config.LeaderElectionConfigurationBuilder.aLeaderElectionConfiguration;

var config = aLeaderElectionConfiguration("my-operator-lease")
    .withLeaseNamespace("operator-namespace")
    .withIdentity(System.getenv("POD_NAME"))
    .withLeaseDuration(Duration.ofSeconds(15))
    .withRenewDeadline(Duration.ofSeconds(10))
    .withRetryPeriod(Duration.ofSeconds(2))
    .build();

var operator = new Operator(o -> o.withLeaderElectionConfiguration(config));
```

### External Configuration

Leader election can also be configured via properties (e.g. environment variables or a config file).

See details under [configurations](configuration.md) page.

## How It Works

1. When leader election is enabled, the operator starts but **does not process events** until it acquires
   the lease.
2. Once leadership is acquired, event processing begins normally.
3. If leadership is lost (e.g. the leader pod becomes unresponsive), another instance acquires the lease
   and takes over reconciliation. The instance that lost the lead is terminated (`System.exit()`)

### Identity and Namespace Inference

If not explicitly set:
- **Identity** is resolved from the `HOSTNAME` environment variable, then the pod name, falling back to a
  random UUID.
- **Lease namespace** defaults to the namespace the operator pod is running in.

## RBAC Requirements

The operator's service account needs permissions to manage Lease objects:

```yaml
- apiGroups: ["coordination.k8s.io"]
  resources: ["leases"]
  verbs: ["create", "update", "get"]
```

JOSDK checks for these permissions at startup and throws a clear error if they are missing.

## Sample E2E Test

A complete working example is available in the
[`leader-election` sample operator](https://github.com/java-operator-sdk/java-operator-sdk/tree/main/sample-operators/leader-election),
including multi-replica deployment manifests and an E2E test that verifies failover behavior.
