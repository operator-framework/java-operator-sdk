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
package io.javaoperatorsdk.operator.baseapi.expectation.onallevent;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.IntegrationTestConstants.GARBAGE_COLLECTION_TIMEOUT;
import static io.javaoperatorsdk.operator.baseapi.expectation.onallevent.ExpectationReconciler.DEPLOYMENT_READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Basic Expectation Pattern with AllEvents Trigger",
    description =
        """
        Demonstrates the basic expectation pattern using ExpectationManager with triggerReconcilerOnAllEvents = true.
        This pattern allows reconcilers to wait for specific conditions to be met (like a Deployment having 3 ready replicas)
        before proceeding with status updates. The test shows both successful expectation fulfillment and timeout handling.
        Requires @ControllerConfiguration(triggerReconcilerOnAllEvents = true) for proper operation.
        """)
class ExpectationIT {

  public static final String TEST_1 = "test1";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder().withReconciler(new ExpectationReconciler()).build();

  @Test
  void testExpectation() {
    extension.getReconcilerOfType(ExpectationReconciler.class).setTimeout(Duration.ofSeconds(30));
    var res = testResource();
    extension.create(res);

    await()
        .timeout(GARBAGE_COLLECTION_TIMEOUT)
        .untilAsserted(
            () -> {
              var actual = extension.get(ExpectationCustomResource.class, TEST_1);
              assertThat(actual.getStatus()).isNotNull();
              assertThat(actual.getStatus().getMessage()).isEqualTo(DEPLOYMENT_READY);
            });
  }

  @Test
  void expectationTimeouts() {
    extension.getReconcilerOfType(ExpectationReconciler.class).setTimeout(Duration.ofMillis(300));
    var res = testResource();
    extension.create(res);

    await()
        .timeout(GARBAGE_COLLECTION_TIMEOUT)
        .untilAsserted(
            () -> {
              var actual = extension.get(ExpectationCustomResource.class, TEST_1);
              assertThat(actual.getStatus()).isNotNull();
              assertThat(actual.getStatus().getMessage()).isEqualTo(DEPLOYMENT_READY);
            });
  }

  private ExpectationCustomResource testResource() {
    var res = new ExpectationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(TEST_1).build());
    return res;
  }
}
