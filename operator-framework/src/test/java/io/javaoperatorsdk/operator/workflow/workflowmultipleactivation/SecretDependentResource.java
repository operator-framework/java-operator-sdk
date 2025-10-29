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
package io.javaoperatorsdk.operator.workflow.workflowmultipleactivation;

import java.util.Base64;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

public class SecretDependentResource
    extends CRUDKubernetesDependentResource<Secret, WorkflowMultipleActivationCustomResource> {

  @Override
  protected Secret desired(
      WorkflowMultipleActivationCustomResource primary,
      Context<WorkflowMultipleActivationCustomResource> context) {
    // basically does not matter since this should not be called
    Secret secret = new Secret();
    secret.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    secret.setData(
        Map.of(
            "data", Base64.getEncoder().encodeToString(primary.getSpec().getValue().getBytes())));
    return secret;
  }
}
