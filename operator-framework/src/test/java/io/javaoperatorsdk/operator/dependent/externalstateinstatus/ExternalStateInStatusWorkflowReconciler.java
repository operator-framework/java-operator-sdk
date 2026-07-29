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
package io.javaoperatorsdk.operator.dependent.externalstateinstatus;

import java.util.concurrent.atomic.AtomicInteger;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.support.ExternalResource;
import io.javaoperatorsdk.operator.support.TestExecutionInfoProvider;

/**
 * Manages an external resource through a managed workflow with a single {@link
 * ExternalStateInStatusDependentResource dependent resource}, while storing the external resource's
 * state - its generated ID - in the <b>status</b> of the custom resource.
 *
 * <p>The managed workflow reconciles the dependent (creating the external resource) just before
 * this {@code reconcile} method runs. The reconciler then persists the external ID into the status
 * with {@link UpdateControl#patchStatus(io.fabric8.kubernetes.api.model.HasMetadata)}. Thanks to
 * the stronger read-after-write consistency for updates, the patched status is placed into the
 * cache, so the next reconciliation - and the dependent's fetch - observe the ID and do not create
 * a duplicate external resource.
 */
@Workflow(dependents = @Dependent(type = ExternalStateInStatusDependentResource.class))
@ControllerConfiguration
public class ExternalStateInStatusWorkflowReconciler
    implements Reconciler<ExternalStateInStatusWorkflowCustomResource>, TestExecutionInfoProvider {

  private final AtomicInteger numberOfExecutions = new AtomicInteger(0);

  @Override
  public UpdateControl<ExternalStateInStatusWorkflowCustomResource> reconcile(
      ExternalStateInStatusWorkflowCustomResource resource,
      Context<ExternalStateInStatusWorkflowCustomResource> context) {
    numberOfExecutions.addAndGet(1);

    var externalResource = context.getSecondaryResource(ExternalResource.class);
    if (externalResource.isEmpty()) {
      return UpdateControl.noUpdate();
    }

    var id = externalResource.orElseThrow().getId();
    if (resource.getStatus() == null || !id.equals(resource.getStatus().getId())) {
      resource.setStatus(new ExternalStateInStatusStatus().setId(id));
      return UpdateControl.patchStatus(resource);
    }
    return UpdateControl.noUpdate();
  }

  @Override
  public int getNumberOfExecutions() {
    return numberOfExecutions.get();
  }
}
