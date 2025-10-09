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
package io.javaoperatorsdk.operator.baseapi.gracefulstop;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.gracefulstop.GracefulStopTestReconciler.RECONCILER_SLEEP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class GracefulStopIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(
              o ->
                  o.withCloseClientOnStop(false)
                      .withReconciliationTerminationTimeout(Duration.ofMillis(RECONCILER_SLEEP)))
          .withReconciler(new GracefulStopTestReconciler())
          .build();

  @Test
  void stopsGracefullyWithTimeoutConfiguration() {
    testGracefulStop(TEST_1, 2);
  }

  private void testGracefulStop(String resourceName, int expectedFinalGeneration) {
    var testRes = operator.create(testResource(resourceName));
    await()
        .untilAsserted(
            () -> {
              var r = operator.get(GracefulStopTestCustomResource.class, resourceName);
              assertThat(r.getStatus()).isNotNull();
              assertThat(r.getStatus().getObservedGeneration()).isEqualTo(1);
              assertThat(
                      operator
                          .getReconcilerOfType(GracefulStopTestReconciler.class)
                          .getNumberOfExecutions())
                  .isEqualTo(1);
            });

    testRes.getSpec().setValue(2);
    operator.replace(testRes);

    await()
        .pollDelay(Duration.ofMillis(50))
        .untilAsserted(
            () ->
                assertThat(
                        operator
                            .getReconcilerOfType(GracefulStopTestReconciler.class)
                            .getNumberOfExecutions())
                    .isEqualTo(2));

    operator.getOperator().stop();

    await()
        .untilAsserted(
            () -> {
              var r = operator.get(GracefulStopTestCustomResource.class, resourceName);
              assertThat(r.getStatus()).isNotNull();
              assertThat(r.getStatus().getObservedGeneration()).isEqualTo(expectedFinalGeneration);
            });
  }

  public GracefulStopTestCustomResource testResource(String name) {
    GracefulStopTestCustomResource resource = new GracefulStopTestCustomResource();
    resource.setMetadata(
        new ObjectMetaBuilder().withName(name).withNamespace(operator.getNamespace()).build());
    resource.setSpec(new GracefulStopTestCustomResourceSpec());
    resource.getSpec().setValue(1);
    return resource;
  }
}
