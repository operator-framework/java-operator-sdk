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
package io.javaoperatorsdk.operator.baseapi.reconciliationtimeout;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * A reconciler that issues repeated fabric8 client update (patch) requests in a loop during its
 * first execution. The reconciliation timeout should interrupt the thread, which interrupts the
 * ongoing HTTP request, proving that actual client requests are cancelled on timeout.
 */
@ControllerConfiguration
public class ReconciliationTimeoutClientInterruptReconciler
    implements Reconciler<ReconciliationTimeoutTestCustomResource> {

  private static final Logger log =
      LoggerFactory.getLogger(ReconciliationTimeoutClientInterruptReconciler.class);

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final AtomicBoolean clientCallInterrupted = new AtomicBoolean(false);

  @Override
  public UpdateControl<ReconciliationTimeoutTestCustomResource> reconcile(
      ReconciliationTimeoutTestCustomResource resource,
      Context<ReconciliationTimeoutTestCustomResource> context)
      throws Exception {

    int executionCount = numberOfExecutions.incrementAndGet();

    // On first execution, repeatedly issue patch requests until the thread is interrupted
    if (executionCount == 1) {
      try {
        var configMap = new ConfigMap();
        configMap.setMetadata(
            new io.fabric8.kubernetes.api.model.ObjectMetaBuilder()
                .withName("timeout-test-cm")
                .withNamespace(resource.getMetadata().getNamespace())
                .build());
        configMap.setData(java.util.Map.of("key", "value"));
        // Create the ConfigMap first
        configMap =
            context
                .getClient()
                .configMaps()
                .inNamespace(resource.getMetadata().getNamespace())
                .resource(configMap)
                .create();

        // Loop issuing update requests; the timeout should interrupt one of them
        long start = System.currentTimeMillis();
        int i = 0;
        while (System.currentTimeMillis() - start
            < ReconciliationTimeoutClientInterruptIT.TIMEOUT_MILLIS * 30) {
          configMap.getData().put("key", "value-" + i++);
          configMap =
              context
                  .getClient()
                  .configMaps()
                  .inNamespace(resource.getMetadata().getNamespace())
                  .resource(configMap)
                  .update();
        }
      } catch (Exception e) {
        // The timeout mechanism interrupts the thread, which causes the fabric8 client call
        // to throw an exception (InterruptedException or wrapped)
        if (isInterruptedCause(e)) {
          clientCallInterrupted.set(true);
          log.info("Interrupted while waiting for reconciliation timeout interrupted", e);
        }
        throw e;
      }
    }

    // On retry (second+ execution), complete immediately with status update
    if (resource.getStatus() == null) {
      resource.setStatus(new ReconciliationTimeoutTestCustomResourceStatus());
    }
    resource.getStatus().setObservedGeneration(resource.getMetadata().getGeneration().intValue());
    resource.getStatus().setReconcileCount(executionCount);

    return UpdateControl.patchStatus(resource);
  }

  private static boolean isInterruptedCause(Throwable e) {
    Throwable current = e;
    while (current != null) {
      if (current instanceof InterruptedException) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  public boolean isClientCallInterrupted() {
    return clientCallInterrupted.get();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
