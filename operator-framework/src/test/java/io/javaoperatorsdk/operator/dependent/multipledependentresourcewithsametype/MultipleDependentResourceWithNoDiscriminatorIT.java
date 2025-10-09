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
package io.javaoperatorsdk.operator.dependent.multipledependentresourcewithsametype;

import java.time.Duration;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Multiple Dependents of Same Type Without Discriminator",
    description =
        """
        Demonstrates managing multiple dependent resources of the same type (ConfigMaps) without \
        using discriminators. The framework uses resource names to differentiate between them, \
        simplifying configuration when distinct names are sufficient for identification.
        """)
class MultipleDependentResourceWithNoDiscriminatorIT {

  public static final String TEST_RESOURCE_NAME = "multipledependentresource-testresource";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(MultipleDependentResourceWithDiscriminatorReconciler.class)
          .waitForNamespaceDeletion(true)
          .build();

  @Test
  void twoConfigMapsHaveBeenCreated() {
    MultipleDependentResourceCustomResourceNoDiscriminator customResource =
        createTestCustomResource();
    operator.create(customResource);

    var reconciler =
        operator.getReconcilerOfType(MultipleDependentResourceWithDiscriminatorReconciler.class);

    await().pollDelay(Duration.ofMillis(300)).until(() -> reconciler.getNumberOfExecutions() <= 1);

    IntStream.of(
            MultipleDependentResourceWithDiscriminatorReconciler.FIRST_CONFIG_MAP_ID,
            MultipleDependentResourceWithDiscriminatorReconciler.SECOND_CONFIG_MAP_ID)
        .forEach(
            configMapId -> {
              ConfigMap configMap =
                  operator.get(ConfigMap.class, customResource.getConfigMapName(configMapId));
              assertThat(configMap).isNotNull();
              assertThat(configMap.getMetadata().getName())
                  .isEqualTo(customResource.getConfigMapName(configMapId));
              assertThat(configMap.getData().get(MultipleDependentResourceConfigMap.DATA_KEY))
                  .isEqualTo(String.valueOf(configMapId));
            });
  }

  public MultipleDependentResourceCustomResourceNoDiscriminator createTestCustomResource() {
    MultipleDependentResourceCustomResourceNoDiscriminator resource =
        new MultipleDependentResourceCustomResourceNoDiscriminator();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName(TEST_RESOURCE_NAME)
            .withNamespace(operator.getNamespace())
            .build());
    return resource;
  }
}
