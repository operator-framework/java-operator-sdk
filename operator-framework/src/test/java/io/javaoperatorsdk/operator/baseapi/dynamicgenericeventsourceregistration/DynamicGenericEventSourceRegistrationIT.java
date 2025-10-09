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
package io.javaoperatorsdk.operator.baseapi.dynamicgenericeventsourceregistration;

import java.time.Duration;

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
    tldr = "Dynamic Generic Event Source Registration",
    description =
        """
        Demonstrates dynamic registration of generic event sources during runtime. The test \
        verifies that event sources can be dynamically added to a reconciler and properly \
        trigger reconciliation when the associated resources change, enabling flexible event \
        source management.
        """)
class DynamicGenericEventSourceRegistrationIT {

  public static final String TEST_RESOURCE_NAME = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(DynamicGenericEventSourceRegistrationReconciler.class)
          .build();

  @Test
  void registersEventSourcesDynamically() {
    var reconciler =
        extension.getReconcilerOfType(DynamicGenericEventSourceRegistrationReconciler.class);
    extension.create(testResource());

    await()
        .pollDelay(Duration.ofMillis(150))
        .untilAsserted(
            () -> {
              var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
              var secret = extension.get(Secret.class, TEST_RESOURCE_NAME);
              assertThat(cm).isNotNull();
              assertThat(secret).isNotNull();
            });
    var executions = reconciler.getNumberOfExecutions();
    assertThat(reconciler.getNumberOfEventSources()).isEqualTo(2);
    assertThat(executions).isLessThanOrEqualTo(3);

    var cm = extension.get(ConfigMap.class, TEST_RESOURCE_NAME);
    cm.getData().put("key2", "val2");

    extension.replace(cm); // triggers the reconciliation

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions() - executions).isEqualTo(2);
            });
    assertThat(reconciler.getNumberOfEventSources()).isEqualTo(2);
  }

  DynamicGenericEventSourceRegistrationCustomResource testResource() {
    var res = new DynamicGenericEventSourceRegistrationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_RESOURCE_NAME).build());
    return res;
  }
}
