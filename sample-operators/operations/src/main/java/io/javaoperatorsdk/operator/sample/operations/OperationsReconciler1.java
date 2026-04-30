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
package io.javaoperatorsdk.operator.sample.operations;

import java.util.List;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.timer.TimerEventSource;
import io.javaoperatorsdk.operator.sample.operations.customresource.OperationsCustomResource1;

import static io.javaoperatorsdk.operator.sample.operations.OperationsReconciler1.NAME;

@ControllerConfiguration(name = NAME)
public class OperationsReconciler1 extends AbstractOperationsReconciler<OperationsCustomResource1> {

  public static final String NAME = "OperationsReconciler1";

  private static final long TIMER_DELAY = 5000;

  private final TimerEventSource<OperationsCustomResource1> timerEventSource;

  public OperationsReconciler1() {
    super(100);
    timerEventSource = new TimerEventSource<>();
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<EventSource<?, OperationsCustomResource1>> prepareEventSources(
      EventSourceContext<OperationsCustomResource1> context) {
    return List.of((EventSource) timerEventSource);
  }

  @Override
  public UpdateControl<OperationsCustomResource1> reconcile(
      OperationsCustomResource1 resource, Context<OperationsCustomResource1> context) {
    var result = super.reconcile(resource, context);
    timerEventSource.scheduleOnce(ResourceID.fromResource(resource), TIMER_DELAY);
    return result;
  }
}
