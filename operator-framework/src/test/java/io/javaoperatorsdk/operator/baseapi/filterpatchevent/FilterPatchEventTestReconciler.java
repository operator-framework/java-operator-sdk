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
package io.javaoperatorsdk.operator.baseapi.filterpatchevent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

import static io.javaoperatorsdk.operator.baseapi.filterpatchevent.FilterPatchEventIT.UPDATED;

@ControllerConfiguration(generationAwareEventProcessing = false)
public class FilterPatchEventTestReconciler
    implements Reconciler<FilterPatchEventTestCustomResource> {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);
  private final AtomicBoolean filterPatchEvent = new AtomicBoolean(false);

  @Override
  public UpdateControl<FilterPatchEventTestCustomResource> reconcile(
      FilterPatchEventTestCustomResource resource,
      Context<FilterPatchEventTestCustomResource> context) {
    numberOfExecutions.incrementAndGet();

    // Update the spec value to trigger a patch operation
    resource.setStatus(new FilterPatchEventTestCustomResourceStatus());
    resource.getStatus().setValue(UPDATED);

    return UpdateControl.patchStatus(resource).filterPatchEvent(filterPatchEvent.get());
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  public void setFilterPatchEvent(boolean b) {
    filterPatchEvent.set(b);
  }
}
