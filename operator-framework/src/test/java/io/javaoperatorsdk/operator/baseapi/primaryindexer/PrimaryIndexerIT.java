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
package io.javaoperatorsdk.operator.baseapi.primaryindexer;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.primaryindexer.AbstractPrimaryIndexerTestReconciler.CONFIG_MAP_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class PrimaryIndexerIT {

  public static final String RESOURCE_NAME1 = "test1";
  public static final String RESOURCE_NAME2 = "test2";

  @RegisterExtension LocallyRunOperatorExtension operator = buildOperator();

  protected LocallyRunOperatorExtension buildOperator() {
    return LocallyRunOperatorExtension.builder()
        .withReconciler(new PrimaryIndexerTestReconciler())
        .build();
  }

  @Test
  void changesToSecondaryResourcesCorrectlyTriggerReconciler() {
    var reconciler = (AbstractPrimaryIndexerTestReconciler) operator.getFirstReconciler();
    operator.create(createTestResource(RESOURCE_NAME1));
    operator.create(createTestResource(RESOURCE_NAME2));

    await()
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME1).get()).isEqualTo(1);
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME2).get()).isEqualTo(1);
            });

    operator.create(configMap());

    await()
        .pollDelay(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME1).get()).isEqualTo(2);
              assertThat(reconciler.getNumberOfExecutions().get(RESOURCE_NAME2).get()).isEqualTo(2);
            });
  }

  private ConfigMap configMap() {
    ConfigMap configMap = new ConfigMap();
    configMap.setMetadata(new ObjectMeta());
    configMap.getMetadata().setName(CONFIG_MAP_NAME);

    return configMap;
  }

  private PrimaryIndexerTestCustomResource createTestResource(String name) {
    PrimaryIndexerTestCustomResource cr = new PrimaryIndexerTestCustomResource();
    cr.setMetadata(new ObjectMeta());
    cr.getMetadata().setName(name);
    cr.setSpec(new PrimaryIndexerTestCustomResourceSpec());
    cr.getSpec().setConfigMapName(CONFIG_MAP_NAME);
    return cr;
  }
}
