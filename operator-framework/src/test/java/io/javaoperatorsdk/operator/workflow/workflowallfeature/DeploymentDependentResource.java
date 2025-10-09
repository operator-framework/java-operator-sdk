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
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;

public class DeploymentDependentResource
    extends CRUDNoGCKubernetesDependentResource<Deployment, WorkflowAllFeatureCustomResource> {

  @Override
  protected Deployment desired(
      WorkflowAllFeatureCustomResource primary, Context<WorkflowAllFeatureCustomResource> context) {
    Deployment deployment =
        ReconcilerUtils.loadYaml(
            Deployment.class,
            WorkflowAllFeatureIT.class,
            "/io/javaoperatorsdk/operator/nginx-deployment.yaml");
    deployment.getMetadata().setName(primary.getMetadata().getName());
    deployment.getSpec().setReplicas(2);
    deployment.getMetadata().setNamespace(primary.getMetadata().getNamespace());
    return deployment;
  }
}
