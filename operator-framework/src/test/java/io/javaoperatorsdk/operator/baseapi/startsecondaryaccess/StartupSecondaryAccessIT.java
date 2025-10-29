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
package io.javaoperatorsdk.operator.baseapi.startsecondaryaccess;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.startsecondaryaccess.StartupSecondaryAccessReconciler.LABEL_KEY;
import static io.javaoperatorsdk.operator.baseapi.startsecondaryaccess.StartupSecondaryAccessReconciler.LABEL_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StartupSecondaryAccessIT {

  public static final int SECONDARY_NUMBER = 200;

  @RegisterExtension
  static LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new StartupSecondaryAccessReconciler())
          .withBeforeStartHook(
              ex -> {
                var primary = new StartupSecondaryAccessCustomResource();
                primary.setMetadata(new ObjectMetaBuilder().withName("test1").build());
                primary = ex.serverSideApply(primary);

                for (int i = 0; i < SECONDARY_NUMBER; i++) {
                  ConfigMap cm = new ConfigMap();
                  cm.setMetadata(
                      new ObjectMetaBuilder()
                          .withLabels(Map.of(LABEL_KEY, LABEL_VALUE))
                          .withNamespace(ex.getNamespace())
                          .withName("cm" + i)
                          .build());
                  cm.addOwnerReference(primary);
                  ex.serverSideApply(cm);
                }
              })
          .build();

  @Test
  void reconcilerSeeAllSecondaryResources() {
    var reconciler = extension.getReconcilerOfType(StartupSecondaryAccessReconciler.class);

    await().untilAsserted(() -> assertThat(reconciler.isReconciled()).isTrue());

    assertThat(reconciler.isSecondaryAndCacheSameAmount()).isTrue();
  }
}
