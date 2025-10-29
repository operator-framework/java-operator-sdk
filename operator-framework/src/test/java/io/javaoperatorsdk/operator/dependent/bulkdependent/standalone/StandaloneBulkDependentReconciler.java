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
package io.javaoperatorsdk.operator.dependent.bulkdependent.standalone;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.CRUDConfigMapBulkDependentResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.ConfigMapDeleterBulkDependentResource;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@ControllerConfiguration
public class StandaloneBulkDependentReconciler
    implements Reconciler<BulkDependentTestCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  private final ConfigMapDeleterBulkDependentResource dependent;

  public StandaloneBulkDependentReconciler() {
    dependent = new CRUDConfigMapBulkDependentResource();
  }

  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    dependent.reconcile(resource, context);

    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }

  @Override
  public List<EventSource<?, BulkDependentTestCustomResource>> prepareEventSources(
      EventSourceContext<BulkDependentTestCustomResource> context) {
    return List.of(dependent.initEventSource(context));
  }
}
