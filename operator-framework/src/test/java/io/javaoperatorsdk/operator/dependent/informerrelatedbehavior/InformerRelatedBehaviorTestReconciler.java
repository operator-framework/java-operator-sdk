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
package io.javaoperatorsdk.operator.dependent.informerrelatedbehavior;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

@Workflow(
    dependents =
        @Dependent(
            name = InformerRelatedBehaviorTestReconciler.CONFIG_MAP_DEPENDENT_RESOURCE,
            type = ConfigMapDependentResource.class))
@ControllerConfiguration(
    name = InformerRelatedBehaviorTestReconciler.INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER)
public class InformerRelatedBehaviorTestReconciler
    implements Reconciler<InformerRelatedBehaviorTestCustomResource>, TestExecutionInfoProvider {

  private static final Logger log =
      LoggerFactory.getLogger(InformerRelatedBehaviorTestReconciler.class);

  public static final String INFORMER_RELATED_BEHAVIOR_TEST_RECONCILER =
      "InformerRelatedBehaviorTestReconciler";
  public static final String CONFIG_MAP_DEPENDENT_RESOURCE = "ConfigMapDependentResource";

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<InformerRelatedBehaviorTestCustomResource> reconcile(
      InformerRelatedBehaviorTestCustomResource resource,
      Context<InformerRelatedBehaviorTestCustomResource> context) {
    numberOfExecutions.addAndGet(1);
    log.info("Reconciled for: {}", ResourceID.fromResource(resource));
    return UpdateControl.noUpdate();
  }

  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
