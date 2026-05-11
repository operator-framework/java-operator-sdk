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
package io.javaoperatorsdk.operator.baseapi.reconciliationtimeout;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.reconciliationtimeout.ReconciliationTimeoutTestReconciler.TIMEOUT_MILLIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class ReconciliationTimeoutIT {

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              new ReconciliationTimeoutTestReconciler(),
              o -> o.withReconciliationTimeout(Duration.ofMillis(TIMEOUT_MILLIS)))
          .build();

  @Test
  void reconciliationTimesOutAndIsRetried() {
    var resource = new ReconciliationTimeoutTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder().withName("test1").withNamespace(operator.getNamespace()).build());
    resource.setSpec(new ReconciliationTimeoutTestCustomResourceSpec());
    resource.getSpec().setValue(1);

    operator.create(resource);

    // The first reconciliation should time out, and on retry (second execution) it should succeed
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var r = operator.get(ReconciliationTimeoutTestCustomResource.class, "test1");
              assertThat(r.getStatus()).isNotNull();
              assertThat(r.getStatus().getObservedGeneration()).isEqualTo(1);
              // At least 2 executions: first one timed out, second (or later) succeeded
              assertThat(r.getStatus().getReconcileCount()).isGreaterThanOrEqualTo(2);
            });

    assertThat(
            operator
                .getReconcilerOfType(ReconciliationTimeoutTestReconciler.class)
                .getNumberOfExecutions())
        .isGreaterThanOrEqualTo(2);
  }
}
