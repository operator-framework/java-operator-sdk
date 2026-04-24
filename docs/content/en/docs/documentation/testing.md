---
title: Testing
weight: 56
---

Testing is a critical part of building reliable operators. JOSDK supports multiple testing
strategies, from fast unit tests that mock the Kubernetes API, to full integration tests that run
your operator against a real cluster.

## Unit Testing Reconcilers

The fastest way to test reconciler logic is to unit test the `reconcile` method directly. You can
construct a mock or stub `Context` and call your reconciler without starting an operator or
connecting to a cluster.

```java
class MyReconcilerTest {

    @Test
    void shouldSetStatusOnReconcile() {
        var client = mock(KubernetesClient.class);
        var context = mock(Context.class);
        when(context.getClient()).thenReturn(client);

        var resource = new MyCustomResource();
        resource.setMetadata(new ObjectMetaBuilder().withName("test").build());
        resource.setSpec(new MySpec());

        var reconciler = new MyReconciler();
        var result = reconciler.reconcile(resource, context);

        assertThat(resource.getStatus().getState()).isEqualTo("Ready");
        assertThat(result.isPatchStatus()).isTrue();
    }
}
```

This approach is useful for testing pure business logic in the reconciler (e.g. computing desired
state, setting status fields, deciding whether to patch or reschedule). It runs in milliseconds
and needs no cluster.

### Mocking Secondary Resources

If your reconciler reads secondary resources from the context, you can stub
`getSecondaryResource`:

```java
var deployment = new DeploymentBuilder()
    .withNewMetadata().withName("my-deploy").endMetadata()
    .withNewStatus().withReadyReplicas(3).endStatus()
    .build();

when(context.getSecondaryResource(Deployment.class)).thenReturn(Optional.of(deployment));
```

## Integration Testing with `LocallyRunOperatorExtension`

For integration tests, JOSDK provides a JUnit 5 extension that starts your operator locally and
connects it to a real Kubernetes cluster (e.g. a local Kind or Minikube cluster). It automatically:

- Creates an isolated test namespace
- Applies CRDs from the project classpath
- Registers your reconcilers and starts the operator
- Cleans up everything after the test

Add dependency to your project:

```xml
<dependency>
    <groupId>io.javaoperatorsdk</groupId>
    <artifactId>operator-framework-junit</artifactId>
    <version>${josdk.version}</version>
    <scope>test</scope>
</dependency>
```

```java
class MyOperatorIT {

    @RegisterExtension
    LocallyRunOperatorExtension extension =
        LocallyRunOperatorExtension.builder()
            .withReconciler(new MyReconciler())
            .build();

    @Test
    void shouldCreateDeploymentForCustomResource() {
        var resource = new MyCustomResource();
        resource.setMetadata(new ObjectMetaBuilder()
            .withName("test-resource")
            .withNamespace(extension.getNamespace())
            .build());
        resource.setSpec(new MySpec());
        resource.getSpec().setReplicas(3);

        extension.create(resource);

        await().atMost(Duration.ofMinutes(1)).untilAsserted(() -> {
            var updated = extension.get(MyCustomResource.class, "test-resource");
            assertThat(updated.getStatus()).isNotNull();
            assertThat(updated.getStatus().getReadyReplicas()).isEqualTo(3);
        });
    }
}
```

See the [Integration Test Index](../testindex/_index.md) for a comprehensive list of
integration test samples covering various use cases.

### Builder Configuration

The builder offers several configuration options:

```java
LocallyRunOperatorExtension.builder()
    .withReconciler(new MyReconciler())
    // Override controller configuration
    .withReconciler(new MyReconciler(), config -> config
        .settingNamespace("specific-namespace")
        .withRetry(new GenericRetry().withLinearRetry()))
    // Pre-deploy infrastructure resources before operator starts
    .withInfrastructure(configMap, secret)
    // Register CRDs for resources not managed by a reconciler
    .withAdditionalCustomResourceDefinition(OtherResource.class)
    // Provide CRD files from custom paths
    .withAdditionalCRD("path/to/my-crd.yaml")
    // Run initialization logic after namespace is created but before operator starts
    .withBeforeStartHook(ext -> {
        ext.create(somePrerequisiteResource());
    })
    // Use a specific Kubernetes client
    .withKubernetesClient(myClient)
    // Reuse the same namespace for all tests in a class
    .oneNamespacePerClass(true)
    // Keep namespace around on test failure for debugging
    .preserveNamespaceOnError(true)
    .build();
```

### Convenience Methods

The extension provides helper methods for working with resources in the test namespace:

```java
// Create a resource in the test namespace
extension.create(resource);

// Get a resource by type and name
MyCustomResource cr = extension.get(MyCustomResource.class, "my-resource");

// Update a resource
extension.update(modifiedResource);

// Delete a resource
extension.delete(resource);

// Server-side apply
extension.serverSideApply(resource);

// Access the underlying Kubernetes client for advanced operations
KubernetesClient client = extension.getKubernetesClient();

// Access the operator instance
Operator operator = extension.getOperator();

// Get a registered reconciler (useful to inspect state)
MyReconciler reconciler = extension.getReconcilerOfType(MyReconciler.class);
```

### Accessing the Reconciler

If your test needs to inspect the reconciler's internal state (e.g. counters, caches), you can
retrieve it from the extension:

```java
@RegisterExtension
LocallyRunOperatorExtension extension =
    LocallyRunOperatorExtension.builder()
        .withReconciler(new MyReconciler())
        .build();

@Test
void shouldReconcileExactlyOnce() {
    extension.create(testResource());

    await().untilAsserted(() -> {
        var reconciler = extension.getReconcilerOfType(MyReconciler.class);
        assertThat(reconciler.getReconcileCount()).isEqualTo(1);
    });
}
```

## Testing with a Cluster-Deployed Operator

For end-to-end tests where the operator runs as a container in the cluster (e.g. to test the
Docker image, RBAC, or resource limits), use `ClusterDeployedOperatorExtension`:

```java
class MyOperatorE2E {

    @RegisterExtension
    ClusterDeployedOperatorExtension extension =
        ClusterDeployedOperatorExtension.builder()
            .withOperatorDeployment(
                client.load(new FileInputStream("k8s/operator.yaml")).items())
            .withDeploymentTimeout(Duration.ofMinutes(2))
            .build();

    @Test
    void operatorShouldReconcile() {
        var resource = new MyCustomResource();
        resource.setMetadata(new ObjectMetaBuilder()
            .withName("test")
            .withNamespace(extension.getNamespace())
            .build());

        extension.create(resource);

        await().atMost(Duration.ofMinutes(3)).untilAsserted(() -> {
            var cr = extension.get(MyCustomResource.class, "test");
            assertThat(cr.getStatus()).isNotNull();
        });
    }
}
```

This extension:

- Deploys the operator YAML manifests (Deployment, ServiceAccount, RBAC, etc.) into the test
  namespace
- Applies CRDs from `./target/classes/META-INF/fabric8/`
- Adjusts `ClusterRoleBinding` subjects to point to the test namespace
- Waits for the operator Deployment to become ready
- Cleans up after the test

See tests in [sample-operators](https://github.com/operator-framework/java-operator-sdk/blob/main/sample-operators)
for usage.

### Choosing Between Local and Cluster-Deployed

| Aspect                     | `LocallyRunOperatorExtension`  | `ClusterDeployedOperatorExtension` |
|----------------------------|--------------------------------|------------------------------------|
| Operator runs              | In the test JVM                | As a Pod in the cluster            |
| Startup time               | Fast                           | Slower (image pull, pod start)     |
| Debugging                  | Attach debugger directly       | Requires remote debugging or logs  |
| Tests                      | RBAC not exercised             | Full RBAC and resource limits      |
| Typical use                | Development, CI integration    | Pre-release E2E validation         |

## Using Fabric8 Mock Server for Fast Integration Tests

The [Fabric8 Kubernetes Mock Server](https://github.com/fabric8io/kubernetes-client/blob/main/doc/KubernetesClientWithMockWebServer.md) provides an in-memory Kubernetes API server that supports
CRUD operations. This is useful for testing reconciler logic that interacts with the Kubernetes
API without needing a real cluster.

Add the dependency:

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-server-mock</artifactId>
    <version>${fabric8-client.version}</version>
    <scope>test</scope>
</dependency>
```

Use `@EnableKubernetesMockClient` to inject a mock client:

```java
@EnableKubernetesMockClient(crud = true)
class MyReconcilerMockTest {

    KubernetesClient client;

    @Test
    void shouldCreateSecondaryResources() {
        // Pre-create resources in the mock server
        client.resource(testConfigMap()).create();

        var context = mock(Context.class);
        when(context.getClient()).thenReturn(client);

        var resource = testCustomResource();
        var reconciler = new MyReconciler();
        reconciler.reconcile(resource, context);

        // Verify that the reconciler created the expected Deployment
        var deployment = client.apps().deployments()
            .inNamespace("test-ns")
            .withName("expected-deploy")
            .get();
        assertThat(deployment).isNotNull();
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(3);
    }
}
```

The `crud = true` flag enables automatic CRUD behavior: resources you create are stored and can be
retrieved, updated, and deleted, simulating a real API server. Without it, you would need to set up
explicit request/response expectations.

## Using Fabric8 `@KubeAPITest` for Realistic API Testing

For tests that need a more realistic Kubernetes API (including watches, status subresources, and
server-side apply), the Fabric8 client provides the
[`@KubeAPITest`](https://github.com/fabric8io/kubernetes-client/blob/main/doc/kube-api-test.md)
annotation. It starts a lightweight API server that behaves more closely to a real cluster than
the mock server.

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-junit-jupiter</artifactId>
    <version>${fabric8-client.version}</version>
    <scope>test</scope>
</dependency>
```

```java
@KubeAPITest
class MyReconcilerKubeAPITest {

    KubernetesClient client;

    @Test
    void shouldHandleStatusUpdates() {
        // The API server supports watches, SSA, and status subresources
        client.resource(testCRD()).create();
        client.resource(testCustomResource()).create();

        var reconciler = new MyReconciler();
        var context = mock(Context.class);
        when(context.getClient()).thenReturn(client);

        var resource = client.resources(MyCustomResource.class)
            .withName("test").get();
        reconciler.reconcile(resource, context);

        var updated = client.resources(MyCustomResource.class)
            .withName("test").get();
        assertThat(updated.getStatus().getState()).isEqualTo("Ready");
    }
}
```

## Multi-Reconciliation Testing Pattern

Operator reconciliation is often a multi-step process. A realistic test exercises your reconciler
through multiple cycles, verifying the state transitions:

```java
@Test
void shouldProgressThroughLifecycle() {
    extension.create(testResource());

    // Step 1: reconciler creates Deployment
    await().untilAsserted(() -> {
        var deploy = extension.get(Deployment.class, "my-deploy");
        assertThat(deploy).isNotNull();
    });

    // Step 2: simulate Deployment becoming ready
    var deploy = extension.get(Deployment.class, "my-deploy");
    deploy.getStatus().setReadyReplicas(
        deploy.getSpec().getReplicas());
    extension.getKubernetesClient().resource(deploy)
        .inNamespace(extension.getNamespace()).patchStatus();

    // Step 3: verify that the custom resource status reflects readiness
    await().untilAsserted(() -> {
        var cr = extension.get(MyCustomResource.class, "test");
        assertThat(cr.getStatus().getState()).isEqualTo("Ready");
    });
}
```

## Configuration via System Properties

The test extensions can be configured via system properties (useful in CI):

| System Property                      | Default | Description                                        |
|--------------------------------------|---------|----------------------------------------------------|
| `josdk.it.preserveNamespaceOnError`  | `false` | Keep namespace when tests fail, for debugging      |
| `josdk.it.skipNamespaceDeletion`     | `false` | Skip namespace cleanup after tests                 |
| `josdk.it.waitForNamespaceDeletion`  | `true`  | Wait for namespace to be fully deleted              |
| `josdk.it.oneNamespacePerClass`      | `false` | Reuse the same namespace for all tests in a class  |
| `josdk.it.namespaceDeleteTimeout`    | `90`    | Namespace deletion timeout in seconds              |
| `testsuite.deleteCRDs`              | `true`  | Delete applied CRDs after tests                    |

Example:

```bash
mvn test -Djosdk.it.preserveNamespaceOnError=true -Djosdk.it.oneNamespacePerClass=true
```
