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
package io.javaoperatorsdk.operator.dependent.bulkdependent.readonly;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestCustomResource;
import io.javaoperatorsdk.operator.dependent.bulkdependent.BulkDependentTestStatus;

@Workflow(
    dependents =
        @Dependent(
            type = ReadOnlyBulkDependentResource.class,
            readyPostcondition = ReadOnlyBulkReadyPostCondition.class))
@ControllerConfiguration
public class ReadOnlyBulkReconciler implements Reconciler<BulkDependentTestCustomResource> {
  @Override
  public UpdateControl<BulkDependentTestCustomResource> reconcile(
      BulkDependentTestCustomResource resource, Context<BulkDependentTestCustomResource> context) {

    var nonReadyDependents =
        context
            .managedWorkflowAndDependentResourceContext()
            .getWorkflowReconcileResult()
            .orElseThrow()
            .getNotReadyDependents();

    BulkDependentTestCustomResource customResource = new BulkDependentTestCustomResource();
    customResource.setMetadata(
        new ObjectMetaBuilder()
            .withName(resource.getMetadata().getName())
            .withNamespace(resource.getMetadata().getNamespace())
            .build());
    var status = new BulkDependentTestStatus();
    status.setReady(nonReadyDependents.isEmpty());
    customResource.setStatus(status);

    return UpdateControl.patchStatus(customResource);
  }
}
