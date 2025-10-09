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
package io.javaoperatorsdk.operator.dependent.createonlyifnotexistsdependentwithssa;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Create-Only Dependent Resources with Server-Side Apply",
    description =
        """
        Demonstrates how to configure a dependent resource that is only created if it doesn't \
        exist, using Server-Side Apply (SSA). This test shows that when a resource already \
        exists, the dependent resource implementation will not modify it, preserving any \
        external changes.
        """)
class CreateOnlyIfNotExistingDependentWithSSAIT {

  public static final String TEST_RESOURCE_NAME = "test1";
  public static final String KEY = "key";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          // for the sake of this test, we allow to manage ConfigMaps with SSA
          // by removing it from the non SSA resources (it is not managed with SSA by default)
          .withConfigurationService(o -> o.withDefaultNonSSAResource(Set.of()))
          .withReconciler(new CreateOnlyIfNotExistingDependentWithSSAReconciler())
          .build();

  @Test
  void createsResourceOnlyIfNotExisting() {
    var cm =
        new ConfigMapBuilder()
            .withMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build())
            .withData(Map.of(KEY, "val"))
            .build();

    extension.create(cm);
    extension.create(testResource());

    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var currentCM = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              assertThat(currentCM.getData()).containsOnlyKeys(KEY);
            });
  }

  CreateOnlyIfNotExistingDependentWithSSACustomResource testResource() {
    var res = new CreateOnlyIfNotExistingDependentWithSSACustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());

    return res;
  }
}
