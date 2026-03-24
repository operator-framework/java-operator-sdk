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

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.ResourceIDMapper;
import io.javaoperatorsdk.operator.processing.dependent.CRUDKubernetesBulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.event.ResourceID;

/**
 * Child bulk dependent resource with an activationCondition.
 *
 * <p>The bug: when NodeDeleteExecutor fires for this resource (because its parent
 * ConfigMapDependentResource has a failing reconcilePrecondition), the event source for Secret has
 * never been registered — NodeReconcileExecutor never ran for this node. NodeDeleteExecutor does
 * NOT call registerOrDeregisterEventSourceBasedOnActivation() before calling delete(), so
 * getSecondaryResources() → eventSourceRetriever.getEventSourceFor(Secret.class) throws
 * NoEventSourceForClassException.
 *
 * <p>This implementation intentionally has no try/catch — it exposes the raw bug.
 */
public class SecretBulkDependentResource
    extends KubernetesDependentResource<Secret, BulkActivationConditionCustomResource>
    implements CRUDKubernetesBulkDependentResource<Secret, BulkActivationConditionCustomResource> {

  public static final String LABEL_KEY = "reproducer";
  public static final String LABEL_VALUE = "bulk-activation-condition";

  @Override
  public Map<ResourceID, Secret> desiredResources(
      BulkActivationConditionCustomResource primary,
      Context<BulkActivationConditionCustomResource> context) {
    var secret = new Secret();
    secret.setMetadata(
        new ObjectMetaBuilder()
            .withName(primary.getMetadata().getName())
            .withNamespace(primary.getMetadata().getNamespace())
            .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
            .build());
    return Map.of(ResourceIDMapper.kubernetesResourceIdMapper().idFor(secret), secret);
  }

  @Override
  public Map<ResourceID, Secret> getSecondaryResources(
      BulkActivationConditionCustomResource primary,
      Context<BulkActivationConditionCustomResource> context) {
    // Deliberately uses getEventSourceFor (singular) — not getSecondaryResourcesAsStream —
    // because the singular variant throws NoEventSourceForClassException when the source is absent.
    // This mirrors the Kroxylicious ClusterRouteDependentResource pattern and exposes the bug:
    // on first reconciliation NodeDeleteExecutor fires before the event source is registered.
    var secrets =
        context
            .eventSourceRetriever()
            .getEventSourceFor(Secret.class)
            .getSecondaryResources(primary);
    return secrets.stream()
        .collect(Collectors.toMap(ResourceID::fromResource, Function.identity()));
  }
}
