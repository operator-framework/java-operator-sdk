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
package io.javaoperatorsdk.operator.baseapi.eventrecorderconfigured;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;

@ControllerConfiguration(generationAwareEventProcessing = false)
public class ConfiguredEventRecorderReconciler
    implements Reconciler<ConfiguredEventRecorderCustomResource> {

  public static final String NAMED_REASON = "StatusReport";
  public static final String AGGREGATED_REASON = "SomethingChanged";

  private final AtomicInteger numberOfExecutions = new AtomicInteger();

  @Override
  public UpdateControl<ConfiguredEventRecorderCustomResource> reconcile(
      ConfiguredEventRecorderCustomResource resource,
      Context<ConfiguredEventRecorderCustomResource> context) {
    var execution = numberOfExecutions.incrementAndGet();
    context.eventRecorder().warn(NAMED_REASON, "status after execution " + execution);
    context
        .eventRecorder()
        .normal(AGGREGATED_REASON, "something changed in execution " + execution);
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
