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

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingCustomResource1;
import io.javaoperatorsdk.operator.sample.metrics.customresource.MetricsHandlingStatus;

@ControllerConfiguration
public class MetricsHandlingReconciler1 implements Reconciler<MetricsHandlingCustomResource1> {

  private static final Logger log = LoggerFactory.getLogger(MetricsHandlingReconciler1.class);

  public MetricsHandlingReconciler1() {}

  @Override
  public List<EventSource<?, MetricsHandlingCustomResource1>> prepareEventSources(
      EventSourceContext<MetricsHandlingCustomResource1> context) {
    return List.of();
  }

  @Override
  public UpdateControl<MetricsHandlingCustomResource1> reconcile(
      MetricsHandlingCustomResource1 resource, Context<MetricsHandlingCustomResource1> context) {

    String name = resource.getMetadata().getName();
    log.info("Reconciling resource: {}", name);

    // Simulate some work
    try {
      Thread.sleep(100);
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
      status.setObservedNumber(spec.getObservedNumber());
    }

    log.info("Successfully reconciled resource: {}", name);
    return UpdateControl.patchStatus(resource);
  }
}
