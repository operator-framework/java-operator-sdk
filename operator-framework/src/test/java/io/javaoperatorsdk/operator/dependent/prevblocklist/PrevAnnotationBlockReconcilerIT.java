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
package io.javaoperatorsdk.operator.dependent.prevblocklist;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class PrevAnnotationBlockReconcilerIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          // Removing resource from blocklist List would result in test failure
          //          .withConfigurationService(
          //              o -> o.previousAnnotationUsageBlocklist(Collections.emptyList()))
          .withReconciler(PrevAnnotationBlockReconciler.class)
          .build();

  @Test
  void doNotUsePrevAnnotationForDeploymentDependent() {
    extension.create(testResource(TEST_1));

    var reconciler = extension.getReconcilerOfType(PrevAnnotationBlockReconciler.class);
    await()
        .pollDelay(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              var deployment = extension.get(Deployment.class, TEST_1);
              assertThat(deployment).isNotNull();
              assertThat(reconciler.getNumberOfExecutions()).isGreaterThan(0).isLessThan(10);
            });
  }

  PrevAnnotationBlockCustomResource testResource(String name) {
    var res = new PrevAnnotationBlockCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(name).build());
    res.setSpec(new PrevAnnotationBlockSpec());
    res.getSpec().setValue("value");
    return res;
  }
}
