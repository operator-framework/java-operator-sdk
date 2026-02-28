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
package io.javaoperatorsdk.operator.sample.metrics;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingSpec;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingStatus;

public abstract class AbstractMetricsHandlingReconciler<
        R extends CustomResource<MetricsHandlingSpec, MetricsHandlingStatus>>
    implements Reconciler<R> {

  private static final Logger log =
      LoggerFactory.getLogger(AbstractMetricsHandlingReconciler.class);

  private final long sleepMillis;

  protected AbstractMetricsHandlingReconciler(long sleepMillis) {
    this.sleepMillis = sleepMillis;
  }

  @Override
  public UpdateControl<R> reconcile(R resource, Context<R> context) {
    String name = resource.getMetadata().getName();
    log.info("Reconciling resource: {}", name);

    try {
      Thread.sleep(sleepMillis + ThreadLocalRandom.current().nextLong(sleepMillis));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted during reconciliation", e);
    }

    if (name.toLowerCase().contains("fail")) {
      log.error("Simulating failure for resource: {}", name);
      throw new IllegalStateException("Simulated reconciliation failure for resource: " + name);
    }

    var status = resource.getStatus();
    if (status == null) {
      status = new MetricsHandlingStatus();
      resource.setStatus(status);
    }

    var spec = resource.getSpec();
    if (spec != null) {
      status.setObservedNumber(spec.getNumber());
    }

    log.info("Successfully reconciled resource: {}", name);
    return UpdateControl.patchStatus(resource);
  }
}
