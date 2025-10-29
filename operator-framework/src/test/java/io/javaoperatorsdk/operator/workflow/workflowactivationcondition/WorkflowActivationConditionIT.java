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
package io.javaoperatorsdk.operator.workflow.workflowactivationcondition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.workflow.workflowactivationcondition.ConfigMapDependentResource.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class WorkflowActivationConditionIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String TEST_DATA = "test data";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(WorkflowActivationConditionReconciler.class)
          .build();

  // Without activation condition this would fail / there would be errors.
  @Test
  void reconciledOnVanillaKubernetesDespiteRouteInWorkflow() {
    extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData()).containsEntry(DATA_KEY, TEST_DATA);
            });
  }

  private WorkflowActivationConditionCustomResource testResource() {
    var res = new WorkflowActivationConditionCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    res.setSpec(new WorkflowActivationConditionSpec());
    res.getSpec().setValue(TEST_DATA);
    return res;
  }
}
