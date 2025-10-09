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
package io.javaoperatorsdk.operator.workflow.workflowallfeature;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;

public class DeploymentReadyCondition
    implements Condition<Deployment, WorkflowAllFeatureCustomResource> {
  @Override
  public boolean isMet(
      DependentResource<Deployment, WorkflowAllFeatureCustomResource> dependentResource,
      WorkflowAllFeatureCustomResource primary,
      Context<WorkflowAllFeatureCustomResource> context) {
    return dependentResource
        .getSecondaryResource(primary, context)
        .map(
            deployment -> {
              var readyReplicas = deployment.getStatus().getReadyReplicas();
              return readyReplicas != null
                  && deployment.getSpec().getReplicas().equals(readyReplicas);
            })
        .orElse(false);
  }
}
