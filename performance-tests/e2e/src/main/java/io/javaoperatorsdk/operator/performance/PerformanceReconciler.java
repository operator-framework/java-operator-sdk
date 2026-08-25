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
package io.javaoperatorsdk.operator.performance;

import java.util.concurrent.atomic.AtomicInteger;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

/**
 * Minimal reconciler: it only records what it has seen in the status subresource. Keeping the
 * reconciliation itself trivial means the measured throughput reflects the cost of the SDK event
 * processing and the API server round trips, not the cost of the business logic.
 */
@ControllerConfiguration
public class PerformanceReconciler implements Reconciler<PerformanceCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<PerformanceCustomResource> reconcile(
      PerformanceCustomResource resource, Context<PerformanceCustomResource> context) {
    numberOfExecutions.incrementAndGet();

    var statusPatch = new PerformanceCustomResource();
    statusPatch.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    var status = new PerformanceCustomResourceStatus();
    status.setObservedGeneration(resource.getMetadata().getGeneration());
    status.setObservedValue(resource.getSpec().getValue());
    statusPatch.setStatus(status);

    return UpdateControl.patchStatus(statusPatch);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
