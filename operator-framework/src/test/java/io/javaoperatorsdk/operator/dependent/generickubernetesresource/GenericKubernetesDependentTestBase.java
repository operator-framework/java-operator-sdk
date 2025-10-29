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
package io.javaoperatorsdk.operator.dependent.generickubernetesresource;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.client.CustomResource;
import io.javaoperatorsdk.operator.dependent.generickubernetesresource.generickubernetesdependentstandalone.ConfigMapGenericKubernetesDependent;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.IntegrationTestConstants.GARBAGE_COLLECTION_TIMEOUT_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public abstract class GenericKubernetesDependentTestBase<
    R extends CustomResource<GenericKubernetesDependentSpec, Void>> {

  public static final String INITIAL_DATA = "Initial data";
  public static final String CHANGED_DATA = "Changed data";
  public static final String TEST_RESOURCE_NAME = "test1";

  @Test
  void testReconciliation() {
    var resource = extension().create(testResource(TEST_RESOURCE_NAME, INITIAL_DATA));

    await()
        .untilAsserted(
            () -> {
              var cm = extension().get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(cm.getData())
                  .containsEntry(ConfigMapGenericKubernetesDependent.KEY, INITIAL_DATA);
            });

    resource.getSpec().setValue(CHANGED_DATA);
    resource = extension().replace(resource);

    await()
        .untilAsserted(
            () -> {
              var cm = extension().get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(cm.getData())
                  .containsEntry(ConfigMapGenericKubernetesDependent.KEY, CHANGED_DATA);
            });

    extension().delete(resource);

    await()
        .timeout(Duration.ofSeconds(GARBAGE_COLLECTION_TIMEOUT_SECONDS))
        .untilAsserted(
            () -> {
              var cm = extension().get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNull();
            });
  }

  public abstract LocallyRunOperatorExtension extension();

  public abstract R testResource(String name, String data);
}
