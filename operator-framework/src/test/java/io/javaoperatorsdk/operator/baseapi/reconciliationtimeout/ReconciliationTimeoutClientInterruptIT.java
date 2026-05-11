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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test verifying that a fabric8 client request in progress is interrupted when
 * reconciliation timeout fires.
 */
class ReconciliationTimeoutClientInterruptIT {

  public static final int TIMEOUT_MILLIS = 300;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(
              new ReconciliationTimeoutClientInterruptReconciler(),
              o -> o.withReconciliationTimeout(Duration.ofMillis(TIMEOUT_MILLIS)))
          .build();

  @Test
  void fabric8ClientRequestIsInterruptedOnTimeout() {
    var resource = new ReconciliationTimeoutTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder()
            .withName("test-client-interrupt")
            .withNamespace(operator.getNamespace())
            .build());
    resource.setSpec(new ReconciliationTimeoutTestCustomResourceSpec());
    resource.getSpec().setValue(1);

    operator.create(resource);

    var reconciler =
        operator.getReconcilerOfType(ReconciliationTimeoutClientInterruptReconciler.class);

    // Wait for the first reconciliation to be interrupted and a subsequent one to succeed
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              var r =
                  operator.get(
                      ReconciliationTimeoutTestCustomResource.class, "test-client-interrupt");
              assertThat(r.getStatus()).isNotNull();
              assertThat(r.getStatus().getReconcileCount()).isGreaterThanOrEqualTo(2);
            });

    // Verify that the client call was indeed interrupted (not just a Thread.sleep)
    assertThat(reconciler.isClientCallInterrupted()).isTrue();
    // Verify that a subsequent reconciliation succeeded after the interrupted one
    assertThat(reconciler.getNumberOfExecutions()).isGreaterThanOrEqualTo(2);
  }
}
