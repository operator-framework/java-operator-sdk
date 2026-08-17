/*
 * Copyright Java Operator SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.javaoperatorsdk.operator.sample;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;

/**
 * Wiring required to run an operator on virtual threads. Used both by {@link
 * VirtualThreadsOperator} and by the tests of this sample, so that the operator behaves the same
 * way when it runs locally and when it runs inside a cluster.
 */
public final class VirtualThreads {

  private VirtualThreads() {}

  /**
   * The Kubernetes client executes its asynchronous tasks - most notably the informer event
   * handlers the framework registers - on the task executor. Backing it with virtual threads means
   * that blocking such a callback no longer blocks a platform thread.
   */
  public static KubernetesClient newKubernetesClient() {
    return new KubernetesClientBuilder()
        .withTaskExecutorSupplier(new VirtualThreadExecutorSupplier())
        .build();
  }

  /**
   * Reconciliations and workflow steps are executed on the executors configured here. Note that
   * {@code concurrentReconciliationThreads} and {@code concurrentWorkflowExecutorThreads} have no
   * effect anymore, since a virtual thread per task executor is not pooled and therefore unbounded.
   * Reconciliations of the same resource are still serialized by the framework.
   */
  public static void configureExecutors(ConfigurationServiceOverrider overrider) {
    overrider
        .withExecutorService(Executors.newVirtualThreadPerTaskExecutor())
        .withWorkflowExecutorService(Executors.newVirtualThreadPerTaskExecutor());
  }

  private static class VirtualThreadExecutorSupplier
      implements KubernetesClientBuilder.ExecutorSupplier {

    @Override
    public Executor get() {
      return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public void onClose(Executor executor) {
      ((ExecutorService) executor).shutdownNow();
    }
  }
}
