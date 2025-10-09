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
package io.javaoperatorsdk.operator.baseapi.event;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class EventSourceTestCustomReconciler
    implements Reconciler<EventSourceTestCustomResource>, TestExecutionInfoProvider {

  public static final int TIMER_PERIOD = 500;
  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<EventSourceTestCustomResource> reconcile(
      EventSourceTestCustomResource resource, Context<EventSourceTestCustomResource> context) {

    numberOfExecutions.addAndGet(1);
    ensureStatusExists(resource);
    resource.getStatus().setState(EventSourceTestCustomResourceStatus.State.SUCCESS);

    return UpdateControl.patchStatus(resource).rescheduleAfter(TIMER_PERIOD);
  }

  private void ensureStatusExists(EventSourceTestCustomResource resource) {
    EventSourceTestCustomResourceStatus status = resource.getStatus();
    if (status == null) {
      status = new EventSourceTestCustomResourceStatus();
      resource.setStatus(status);
    }
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
