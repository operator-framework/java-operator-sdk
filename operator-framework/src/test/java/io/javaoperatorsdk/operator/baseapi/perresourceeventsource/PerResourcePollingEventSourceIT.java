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
package io.javaoperatorsdk.operator.baseapi.perresourceeventsource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Per-resource polling event source implementation",
    description =
        """
        Shows how to implement a per-resource polling event source where each primary resource has \
        its own polling schedule to fetch external state. This is useful for integrating \
        with external systems that don't support event-driven notifications.
        """)
class PerResourcePollingEventSourceIT {

  public static final String NAME_1 = "name1";
  public static final String NAME_2 = "name2";

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withReconciler(new PerResourcePollingEventSourceTestReconciler())
          .build();

  /**
   * This is kinda some test to verify that the implementation of PerResourcePollingEventSource
   * works with the underling mechanisms in event source manager and other parts of the system.
   */
  @Test
  void fetchedAndReconciledMultipleTimes() {
    operator.create(resource(NAME_1));
    operator.create(resource(NAME_2));

    var reconciler =
        operator.getReconcilerOfType(PerResourcePollingEventSourceTestReconciler.class);
    await()
        .untilAsserted(
            () -> {
              assertThat(reconciler.getNumberOfExecutions(NAME_1)).isGreaterThan(2);
              assertThat(reconciler.getNumberOfFetchExecution(NAME_1)).isGreaterThan(2);
              assertThat(reconciler.getNumberOfExecutions(NAME_2)).isGreaterThan(2);
              assertThat(reconciler.getNumberOfFetchExecution(NAME_2)).isGreaterThan(2);
            });
  }

  private PerResourceEventSourceCustomResource resource(String name) {
    var res = new PerResourceEventSourceCustomResource();
    res.setMetadata(new ObjectMetaBuilder().withName(name).build());
    return res;
  }
}
