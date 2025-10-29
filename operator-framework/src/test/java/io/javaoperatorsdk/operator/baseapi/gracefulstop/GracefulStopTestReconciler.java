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
package io.javaoperatorsdk.operator.baseapi.gracefulstop;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration
public class GracefulStopTestReconciler implements Reconciler<GracefulStopTestCustomResource> {

  public static final int RECONCILER_SLEEP = 1000;

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<GracefulStopTestCustomResource> reconcile(
      GracefulStopTestCustomResource resource, Context<GracefulStopTestCustomResource> context)
      throws InterruptedException {

    numberOfExecutions.addAndGet(1);
    resource.setStatus(new GracefulStopTestCustomResourceStatus());
    resource.getStatus().setObservedGeneration(resource.getMetadata().getGeneration());
    Thread.sleep(RECONCILER_SLEEP);

    return UpdateControl.patchStatus(resource);
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
