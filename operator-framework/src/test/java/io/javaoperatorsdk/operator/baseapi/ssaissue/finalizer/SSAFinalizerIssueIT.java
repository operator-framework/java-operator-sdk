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
package io.javaoperatorsdk.operator.baseapi.ssaissue.finalizer;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.dsl.base.PatchContext;
import io.fabric8.kubernetes.client.dsl.base.PatchType;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Server-Side Apply Finalizer Field Manager Issue",
    description =
        """
        Demonstrates a potential issue with Server-Side Apply (SSA) when adding finalizers. \
        When a resource is created with the same field manager used by the controller, adding \
        a finalizer can unexpectedly remove other spec fields, showcasing field manager \
        ownership conflicts in SSA.
        """)
class SSAFinalizerIssueIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new SSAFinalizerIssueReconciler())
          .build();

  /**
   * Showcases possibly a case with SSA: When the resource is created with the same field manager
   * that used by the controller, when adding a finalizer, it deletes other parts of the spec.
   */
  @Test
  void addingFinalizerRemoveListValues() {
    var fieldManager =
        extension
            .getRegisteredControllerForReconcile(SSAFinalizerIssueReconciler.class)
            .getConfiguration()
            .fieldManager();

    extension
        .getKubernetesClient()
        .resource(testResource())
        .inNamespace(extension.getNamespace())
        .patch(
            new PatchContext.Builder()
                .withFieldManager(fieldManager)
                .withForce(true)
                .withPatchType(PatchType.SERVER_SIDE_APPLY)
                .build());

    await()
        .untilAsserted(
            () -> {
              var actual = extension.get(SSAFinalizerIssueCustomResource.class, TEST_1);
              assertThat(actual.getFinalizers()).hasSize(1);
              assertThat(actual.getSpec()).isNull();
            });
  }

  SSAFinalizerIssueCustomResource testResource() {
    var res = new SSAFinalizerIssueCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    res.setSpec(new SSAFinalizerIssueSpec());
    res.getSpec().setValue("val");
    res.getSpec().setList(List.of("val1", "val2"));
    return res;
  }
}
