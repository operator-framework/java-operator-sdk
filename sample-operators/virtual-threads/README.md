# Virtual Threads Sample

This sample runs an operator on [Java virtual threads](https://openjdk.org/jeps/444).
**It requires Java 21 or newer**, therefore the module is only part of the build when the JDK
used is at least version 21 (see the profile in the parent `sample-operators/pom.xml`).

Two independent places execute the code of an operator, both are configured in
[`VirtualThreads`](src/main/java/io/javaoperatorsdk/operator/sample/VirtualThreads.java):

1. The framework's executors, used to run reconciliations and workflow steps:

   ```java
   new Operator(overrider -> overrider
       .withExecutorService(Executors.newVirtualThreadPerTaskExecutor())
       .withWorkflowExecutorService(Executors.newVirtualThreadPerTaskExecutor()));
   ```

2. The Kubernetes client's task executor, used for the asynchronous tasks of the client, most
   notably the informer event handlers registered by the framework:

   ```java
   new KubernetesClientBuilder()
       .withTaskExecutorSupplier(new KubernetesClientBuilder.ExecutorSupplier() {
         @Override
         public Executor get() {
           return Executors.newVirtualThreadPerTaskExecutor();
         }

         @Override
         public void onClose(Executor executor) {
           ((ExecutorService) executor).shutdownNow();
         }
       })
       .build();
   ```

Note that a virtual thread per task executor is not a pool, therefore
`concurrentReconciliationThreads` and `concurrentWorkflowExecutorThreads` have no effect: the
number of parallel reconciliations is not limited anymore. Reconciliations of the same resource
are still serialized by the framework, as with the default executors.

The [reconciler](src/main/java/io/javaoperatorsdk/operator/sample/VirtualThreadsReconciler.java)
of this sample simulates a slow remote call by sleeping for a second, and records in the status
whether it was reconciled on a virtual thread. Since a virtual thread does not occupy a platform
thread while it waits, an arbitrary number of such reconciliations can run in parallel.

## Running the Sample

```shell
kubectl apply -f target/classes/META-INF/fabric8/virtualthreadscustomresources.sample.javaoperatorsdk-v1.yml
mvn exec:java -Dexec.mainClass="io.javaoperatorsdk.operator.sample.VirtualThreadsOperator"
```

Then create a custom resource:

```shell
kubectl apply -f k8s/virtual-threads-custom-resource.yaml
```

The status of the resource shows that the reconciliation happened on a virtual thread:

```yaml
status:
  observedValue: "initial value"
  reconciledOnVirtualThread: true
```
