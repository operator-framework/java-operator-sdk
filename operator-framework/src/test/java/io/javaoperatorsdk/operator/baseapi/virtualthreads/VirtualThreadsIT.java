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
package io.javaoperatorsdk.operator.baseapi.virtualthreads;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.javaoperatorsdk.annotation.Sample;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;

import static io.javaoperatorsdk.operator.baseapi.virtualthreads.VirtualThreadsTestReconciler.onVirtualThread;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Sample(
    tldr = "Running reconciliations on virtual threads",
    description =
        """
        Demonstrates how to make the framework execute its concurrent work on virtual threads by \
        simply setting a flag on the ConfigurationService. Virtual threads make the blocking calls \
        a reconciler typically performs much cheaper, while the configured concurrency limits are \
        still enforced: the test verifies that the reconciler runs on virtual threads and that no \
        more than the configured number of reconciliations happen at the same time.
        """)
class VirtualThreadsIT {

  static final int CONCURRENT_RECONCILIATION_THREADS = 2;
  static final int NUMBER_OF_RESOURCES = 10;

  /**
   * Virtual threads require Java 21, on an older JVM the framework transparently falls back to
   * platform threads, which this test also covers since only the concurrency assertions apply then.
   */
  private static final boolean VIRTUAL_THREADS_SUPPORTED = Runtime.version().feature() >= 21;

  @RegisterExtension
  LocallyRunOperatorExtension operator =
      LocallyRunOperatorExtension.builder()
          .withConfigurationService(
              o ->
                  o.withUseVirtualThreads(true)
                      .withConcurrentReconciliationThreads(CONCURRENT_RECONCILIATION_THREADS))
          .withReconciler(new VirtualThreadsTestReconciler())
          .build();

  @Test
  void reconciliationsRunOnVirtualThreadsWithinTheConfiguredConcurrency() {
    // the test itself runs on a platform thread, so the reconciler assertion below can only pass
    // if the framework actually switched to virtual threads
    assertThat(onVirtualThread()).isFalse();

    IntStream.range(0, NUMBER_OF_RESOURCES).forEach(i -> operator.create(testResource(i)));

    var reconciler = operator.getReconcilerOfType(VirtualThreadsTestReconciler.class);
    await()
        .atMost(2, TimeUnit.MINUTES)
        .untilAsserted(
            () ->
                assertThat(reconciler.getNumberOfExecutions())
                    .isGreaterThanOrEqualTo(NUMBER_OF_RESOURCES));

    // parallelism is retained: reconciliations do happen concurrently, but never more than the
    // configured number of them
    assertThat(reconciler.getMaxConcurrentReconciliations())
        .isEqualTo(CONCURRENT_RECONCILIATION_THREADS);
    assertThat(reconciler.allExecutionsOnVirtualThreads()).isEqualTo(VIRTUAL_THREADS_SUPPORTED);
  }

  private VirtualThreadsCustomResource testResource(int index) {
    var resource = new VirtualThreadsCustomResource();
    resource.setMetadata(new ObjectMeta());
    resource.getMetadata().setName("virtual-threads-test-" + index);
    return resource;
  }
}
