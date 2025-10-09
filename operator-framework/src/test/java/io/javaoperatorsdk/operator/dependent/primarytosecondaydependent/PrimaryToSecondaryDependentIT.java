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
package io.javaoperatorsdk.operator.dependent.primarytosecondaydependent;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.dependent.primarytosecondaydependent.ConfigMapDependent.TEST_CONFIG_MAP_NAME;
import static io.javaoperatorsdk.operator.dependent.primarytosecondaydependent.ConfigMapReconcilePrecondition.DO_NOT_RECONCILE;
import static io.javaoperatorsdk.operator.dependent.primarytosecondaydependent.PrimaryToSecondaryDependentReconciler.DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PrimaryToSecondaryDependentIT {

  public static final String TEST_CR_NAME = "test1";
  public static final String TEST_DATA = "testData";
  public @RegisterExtension LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new PrimaryToSecondaryDependentReconciler())
          .build();

  @Test
  void testPrimaryToSecondaryInDependentResources() {
    var reconciler = operator.getReconcilerOfType(PrimaryToSecondaryDependentReconciler.class);
    var cm = operator.create(configMap(DO_NOT_RECONCILE));
    operator.create(testCustomResource());

    await()
        .pollDelay(Duration.ofMillis(250))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isPositive();
              assertThat(operator.get(Secret.class, TEST_CR_NAME)).isNull();
            });

    cm.setData(Map.of(DATA_KEY, TEST_DATA));
    var executions = reconciler.getNumberOfExecutions();
    operator.replace(cm);

    await()
        .pollDelay(Duration.ofMillis(250))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(executions);
              var secret = operator.get(Secret.class, TEST_CR_NAME);
              assertThat(secret).isNotNull();
              assertThat(secret.getData().get(DATA_KEY)).isEqualTo(TEST_DATA);
            });
  }

  PrimaryToSecondaryDependentCustomResource testCustomResource() {
    var res = new PrimaryToSecondaryDependentCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_CR_NAME).build());
    res.setSpec(new PrimaryToSecondaryDependentSpec());
    res.getSpec().setConfigMapName(TEST_CONFIG_MAP_NAME);
    return res;
  }

  ConfigMap configMap(String data) {
    var cm = new ConfigMap();
    cm.setMetadata(new ObjectMetaBuilder().withName(TEST_CONFIG_MAP_NAME).build());
    cm.setData(Map.of(DATA_KEY, data));
    return cm;
  }
}
