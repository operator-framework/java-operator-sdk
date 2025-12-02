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
package io.javaoperatorsdk.operator.workflow.getnonactivesecondary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Workflow Functions on Vanilla Kubernetes Despite Inactive Resources",
    description =
        """
        Verifies that workflows function correctly on vanilla Kubernetes even when they include \
        resources that are not available on the platform (like OpenShift Routes). The operator \
        successfully reconciles by skipping inactive dependents based on activation conditions, \
        demonstrating platform-agnostic operator design.
        """)
public class WorkflowInactiveDependentAccessIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowActivationConditionReconciler.class)
          .build();

  @Test
  void reconciledOnVanillaKubernetesDespiteRouteInWorkflow() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              assertThat(
                      extension
                          .getReconcilerOfType(WorkflowActivationConditionReconciler.class)
                          .getNumberOfReconciliationExecution())
                  .isEqualTo(1);
            });
  }

  private GetNonActiveSecondaryCustomResource testResource() {
    var res = new GetNonActiveSecondaryCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
