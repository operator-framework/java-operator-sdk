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

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class ReconciliationTimeoutTestReconciler
    implements Reconciler<ReconciliationTimeoutTestCustomResource> {

  public static final int TIMEOUT_MILLIS = 300;
  public static final int LONG_RUNNING_MILLIS = 3000;

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<ReconciliationTimeoutTestCustomResource> reconcile(
      ReconciliationTimeoutTestCustomResource resource,
      Context<ReconciliationTimeoutTestCustomResource> context)
      throws InterruptedException {

    int executionCount = numberOfExecutions.incrementAndGet();

    // First execution sleeps longer than the timeout to trigger timeout
    if (executionCount == 1) {
      Thread.sleep(LONG_RUNNING_MILLIS);
    }

    // On retry (second+ execution), complete immediately
    if (resource.getStatus() == null) {
      resource.setStatus(new ReconciliationTimeoutTestCustomResourceStatus());
    }
    resource.getStatus().setObservedGeneration(resource.getMetadata().getGeneration().intValue());
    resource.getStatus().setReconcileCount(executionCount);

    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
