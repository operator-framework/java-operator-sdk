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
package io.javaoperatorsdk.operator.baseapi.expectation.periodicclean;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.expectation.periodicclean.PeriodicCleanerExpectationReconciler.DEPLOYMENT_READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Expectation Pattern with Periodic Cleanup",
    description =
        """
        Demonstrates the PeriodicCleanerExpectationManager pattern which provides automatic cleanup of stale expectations.
        This pattern works without requiring @ControllerConfiguration(triggerReconcilerOnAllEvents = true) and automatically
        cleans up stale expectations periodically (default: 1 minute). This is ideal for 'set and forget' scenarios where
        you want the same expectation API and functionality as the regular ExpectationManager but with automatic lifecycle management.\
        """)
class PeriodicCleanerExpectationIT {

  public static final String TEST_1 = "test1";
  public static final String TEST_2 = "test2";

  @RegisterExtension
  LocallyRunOperatorExtension extension =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new PeriodicCleanerExpectationReconciler())
          .build();

  @Test
  void testPeriodicCleanerExpectationBasicFlow() {
    extension
        .getReconcilerOfType(PeriodicCleanerExpectationReconciler.class)
        .setTimeout(Duration.ofSeconds(30));
    var res = testResource();
    extension.create(res);

    await()
        .untilAsserted(
            () -> {
              var actual = extension.get(PeriodicCleanerExpectationCustomResource.class, TEST_1);
              assertThat(actual.getStatus()).isNotNull();
              assertThat(actual.getStatus().getMessage()).isEqualTo(DEPLOYMENT_READY);
            });
  }

  @Test
  void demonstratesNoTriggerReconcilerOnAllEventsNeededForCleanup() {
    // This test demonstrates that PeriodicCleanerExpectationManager works
    // without @ControllerConfiguration(triggerReconcilerOnAllEvents = true)

    // The PeriodicCleanerExpectationReconciler doesn't use triggerReconcilerOnAllEvents = true
    // yet expectations still work properly due to the periodic cleanup functionality

    var reconciler = extension.getReconcilerOfType(PeriodicCleanerExpectationReconciler.class);
    reconciler.setTimeout(Duration.ofSeconds(30));

    var res = testResource("no-trigger-test");
    var created = extension.create(res);

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getExpectationManager().getExpectation(created)).isPresent();
            });

    extension.delete(res);

    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getExpectationManager().getExpectation(created)).isEmpty();
            });
  }

  private PeriodicCleanerExpectationCustomResource testResource() {
    return testResource(TEST_1);
  }

  private PeriodicCleanerExpectationCustomResource testResource(String name) {
    var res = new PeriodicCleanerExpectationCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return res;
  }
}
