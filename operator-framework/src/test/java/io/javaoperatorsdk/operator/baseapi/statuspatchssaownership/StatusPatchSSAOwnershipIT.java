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
package io.javaoperatorsdk.operator.baseapi.statuspatchssaownership;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.ReconcilerUtilsInternal;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.statuspatchssaownership.StatusPatchSSAOwnershipReconciler.MESSAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Status Patching with SSA Owns Only the Status",
    description =
        """
        Verifies that patching the status with Server-Side Apply through UpdateControl, even when \
        the reconciler returns the reconciled resource itself with its labels and spec, leaves the \
        controller as owner of the status only. Up to Kubernetes 1.35 the API server records the \
        controller as owner of every field in the status apply body, so another applier of the \
        labels would otherwise conflict with the controller.
        """)
class StatusPatchSSAOwnershipIT {

  static final String RESOURCE_NAME = "test";
  static final String LABEL = "app.kubernetes.io/version";
  static final String OTHER_MANAGER = "other-manager";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new StatusPatchSSAOwnershipReconciler())
          .build();

  @Test
  void statusPatchOwnsOnlyTheStatus() {
    applyWithLabelAsOtherManager("1");

    await()
        .untilAsserted(
            () -> {
              var actual =
                  extension.get(StatusPatchSSAOwnershipCustomResource.class, RESOURCE_NAME);
              assertThat(actual.getStatus()).isNotNull();
              assertThat(actual.getStatus().getMessage()).isEqualTo(MESSAGE);
            });

    var actual = extension.get(StatusPatchSSAOwnershipCustomResource.class, RESOURCE_NAME);
    var controllerStatusEntry =
        actual.getMetadata().getManagedFields().stream()
            .filter(entry -> "status".equals(entry.getSubresource()))
            .findFirst()
            .orElseThrow();
    assertThat(controllerStatusEntry.getManager())
        .isEqualTo(
            ReconcilerUtilsInternal.getDefaultNameFor(StatusPatchSSAOwnershipReconciler.class));
    assertThat(controllerStatusEntry.getFieldsV1().getAdditionalProperties())
        .containsOnlyKeys("f:status");

    var relabelled = applyWithLabelAsOtherManager("2");
    assertThat(relabelled.getMetadata().getLabels()).containsEntry(LABEL, "2");
  }

  private StatusPatchSSAOwnershipCustomResource applyWithLabelAsOtherManager(String version) {
    var resource = new StatusPatchSSAOwnershipCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(RESOURCE_NAME)
            .withNamespace(extension.getNamespace())
            .withLabels(Map.of(LABEL, version))
            .build());
    resource.setSpec(new StatusPatchSSAOwnershipSpec().setValue("value"));
    return extension
        .getKubernetesClient()
        .resource(resource)
        .patch(
            new PatchContext.Builder()
                .withFieldManager(OTHER_MANAGER)
                .withPatchType(PatchType.SERVER_SIDE_APPLY)
                .build());
  }
}
