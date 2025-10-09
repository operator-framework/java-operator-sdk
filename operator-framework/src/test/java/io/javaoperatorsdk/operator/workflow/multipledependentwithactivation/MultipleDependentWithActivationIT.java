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
package io.javaoperatorsdk.operator.workflow.multipledependentwithactivation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Multiple Dependents with Activation Conditions",
    description =
        """
        Demonstrates how to use activation conditions with multiple dependent resources. This test \
        shows how different dependent resources can be dynamically enabled or disabled \
        based on runtime conditions, allowing flexible workflow behavior that adapts to \
        changing requirements.
        """)
public class MultipleDependentWithActivationIT {

  public static final String INITIAL_VALUE = "initial_value";
  public static final String CHANGED_VALUE = "changed_value";
  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new MultipleDependentActivationReconciler())
          .build();

  @Test
  void bothDependentsWithActivationAreHandled() {
    var resource = extension.create(testResource());

    await()
        .untilAsserted(
            () -> {
              var cm1 =
                  extension.get(
                      ConfigMap.class, TEST_RESOURCE_NAME + ConfigMapDependentResource1.SUFFIX);
              var cm2 =
                  extension.get(
                      ConfigMap.class, TEST_RESOURCE_NAME + ConfigMapDependentResource2.SUFFIX);
              var secret = extension.get(Secret.class, TEST_RESOURCE_NAME);
              assertThat(secret).isNotNull();
              assertThat(cm1).isNull();
              assertThat(cm2).isNull();
            });

    ActivationCondition.MET = true;
    resource.getSpec().setValue(CHANGED_VALUE);
    extension.replace(resource);

    await()
        .untilAsserted(
            () -> {
              var cm1 =
                  extension.get(
                      ConfigMap.class, TEST_RESOURCE_NAME + ConfigMapDependentResource1.SUFFIX);
              var cm2 =
                  extension.get(
                      ConfigMap.class, TEST_RESOURCE_NAME + ConfigMapDependentResource2.SUFFIX);
              var secret = extension.get(Secret.class, TEST_RESOURCE_NAME);

              assertThat(secret).isNotNull();
              assertThat(cm1).isNotNull();
              assertThat(cm2).isNotNull();
              assertThat(cm1.getData())
                  .containsEntry(
                      ConfigMapDependentResource1.DATA_KEY,
                      CHANGED_VALUE + ConfigMapDependentResource1.SUFFIX);
              assertThat(cm2.getData())
                  .containsEntry(
                      ConfigMapDependentResource2.DATA_KEY,
                      CHANGED_VALUE + ConfigMapDependentResource2.SUFFIX);
            });
  }

  MultipleDependentActivationCustomResource testResource() {
    var res = new MultipleDependentActivationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    res.setSpec(new MultipleDependentActivationSpec());
    res.getSpec().setValue(INITIAL_VALUE);

    return res;
  }
}
