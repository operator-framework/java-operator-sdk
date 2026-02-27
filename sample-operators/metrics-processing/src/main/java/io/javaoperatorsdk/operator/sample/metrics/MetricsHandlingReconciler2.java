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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingCustomResource2;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingStatus;

@ControllerConfiguration
public class MetricsHandlingReconciler2 implements Reconciler<MetricsHandlingCustomResource2> {

  private static final Logger log = LoggerFactory.getLogger(MetricsHandlingReconciler2.class);

  public MetricsHandlingReconciler2() {}

  @Override
  public UpdateControl<MetricsHandlingCustomResource2> reconcile(
      MetricsHandlingCustomResource2 resource, Context<MetricsHandlingCustomResource2> context) {

    String name = resource.getMetadata().getName();
    log.info("Reconciling resource: {}", name);

    // Simulate some work (slightly different timing than reconciler1)
    try {
      Thread.sleep(150);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted during reconciliation", e);
    }

    // Throw exception for resources with names containing "fail" or "error"
    if (name.toLowerCase().contains("fail") || name.toLowerCase().contains("error")) {
      log.error("Simulating failure for resource: {}", name);
      throw new IllegalStateException("Simulated reconciliation failure for resource: " + name);
    }

    // Update status
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
