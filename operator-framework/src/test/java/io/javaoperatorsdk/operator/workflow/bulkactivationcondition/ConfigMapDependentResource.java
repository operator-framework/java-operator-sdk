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
package io.javaoperatorsdk.operator.workflow.bulkactivationcondition;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;

/**
 * Parent dependent resource. Its reconcile precondition always fails, which causes JOSDK to call
 * markDependentsForDelete() on its children — triggering NodeDeleteExecutor for SecretBulkDependent
 * before that resource's event source has ever been registered.
 */
public class ConfigMapDependentResource
    extends CRUDKubernetesDependentResource<ConfigMap, BulkActivationConditionCustomResource> {

  @Override
  protected ConfigMap desired(
      BulkActivationConditionCustomResource primary,
      Context<BulkActivationConditionCustomResource> context) {
    var cm = new ConfigMap();
    cm.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .build());
    return cm;
  }
}
